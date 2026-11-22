package com.example.data.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.model.PdfOrientation
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class GeneratedPdfResult(
    val pdfFile: File,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val isPasswordProtected: Boolean = false
)

@Singleton
class PdfGenerator @Inject constructor(
    private val fileStorage: LocalFileStorage
) {
    suspend fun generatePdf(
        imagePaths: List<String>,
        config: PdfGenerationConfig,
        onProgress: (currentPage: Int, totalPages: Int) -> Unit
    ): GeneratedPdfResult = withContext(Dispatchers.IO) {
        val total = imagePaths.size
        val outFile = fileStorage.createPdfFile(config.documentName)
        val password = config.password?.takeIf { it.isNotEmpty() }
        val writeTarget = if (password != null) {
            fileStorage.createTempPdfFile("raw_pdf")
        } else {
            outFile
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        var renderedCount = 0
        val platformPdfOk = runCatching {
            val pdfDocument = PdfDocument()
            try {
                imagePaths.forEachIndexed { index, path ->
                    onProgress(index + 1, total)
                    val bitmap = fileStorage.loadBitmap(path, config.quality.maxPageSidePx)
                        ?: return@forEachIndexed

                    val (pageW, pageH) = resolvePageDimensions(
                        bitmapWidth = bitmap.width,
                        bitmapHeight = bitmap.height,
                        paperSize = config.paperSize,
                        orientation = config.orientation
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, renderedCount + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas: Canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = if (config.includeMargins && config.paperSize != PaperSize.ORIGINAL_RATIO) {
                        min(pageW, pageH) * 0.04f
                    } else {
                        0f
                    }

                    val availW = max(1f, pageW - margin * 2f)
                    val availH = max(1f, pageH - margin * 2f)
                    val scale = min(availW / max(1, bitmap.width), availH / max(1, bitmap.height))
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f

                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(left, top, left + drawW, top + drawH),
                        paint
                    )
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                    renderedCount++
                }

                FileOutputStream(writeTarget).use { stream ->
                    pdfDocument.writeTo(stream)
                }
            } finally {
                runCatching { pdfDocument.close() }
            }
        }.isSuccess

        if (!platformPdfOk) {
            renderedCount = writePdfWithPdfBoxFallback(
                imagePaths = imagePaths,
                config = config,
                destination = writeTarget,
                onProgress = onProgress
            )
        }

        var encryptedSuccessfully = false
        if (password != null) {
            try {
                encryptedSuccessfully = encryptPdfFile(
                    sourceFile = writeTarget,
                    destinationFile = outFile,
                    userPassword = password,
                    ownerPassword = password
                )
                if (!encryptedSuccessfully) {
                    // Do not silently leave unencrypted file if encryption was requested and failed
                    outFile.delete()
                    throw IllegalStateException("Unable to encrypt PDF with password.")
                }
            } finally {
                writeTarget.delete()
            }
        }

        GeneratedPdfResult(
            pdfFile = outFile,
            pageCount = renderedCount,
            fileSizeBytes = outFile.length(),
            isPasswordProtected = encryptedSuccessfully
        )
    }

    suspend fun encryptPdfFile(
        sourceFile: File,
        destinationFile: File,
        userPassword: String,
        ownerPassword: String = userPassword
    ): Boolean = withContext(Dispatchers.IO) {
        if (userPassword.isEmpty() || !sourceFile.exists()) return@withContext false
        fileStorage.ensurePdfBoxInitialized()
        runCatching {
            PDDocument.load(sourceFile).use { pdDoc ->
                val permissions = AccessPermission().apply {
                    setCanPrint(true)
                    setCanExtractContent(true)
                }
                val policy = StandardProtectionPolicy(ownerPassword, userPassword, permissions).apply {
                    encryptionKeyLength = 128
                    isPreferAES = true
                }
                pdDoc.protect(policy)
                destinationFile.parentFile?.mkdirs()
                pdDoc.save(destinationFile)
            }
            destinationFile.exists() && destinationFile.length() > 0L
        }.getOrDefault(false)
    }

    suspend fun encryptExistingPdfInPlace(pdfPath: String, password: String): Long? = withContext(Dispatchers.IO) {
        if (password.isEmpty()) return@withContext null
        val targetFile = File(pdfPath)
        if (!targetFile.exists()) return@withContext null
        val tempEncrypted = fileStorage.createTempPdfFile("enc_replace")
        try {
            val ok = encryptPdfFile(
                sourceFile = targetFile,
                destinationFile = tempEncrypted,
                userPassword = password,
                ownerPassword = password
            )
            if (!ok) return@withContext null
            tempEncrypted.copyTo(targetFile, overwrite = true)
            targetFile.length()
        } finally {
            tempEncrypted.delete()
        }
    }

    private fun writePdfWithPdfBoxFallback(
        imagePaths: List<String>,
        config: PdfGenerationConfig,
        destination: File,
        onProgress: (currentPage: Int, totalPages: Int) -> Unit
    ): Int {
        fileStorage.ensurePdfBoxInitialized()
        val total = imagePaths.size
        var count = 0
        PDDocument().use { pdDoc ->
            imagePaths.forEachIndexed { index, path ->
                onProgress(index + 1, total)
                val bitmap = fileStorage.loadBitmap(path, config.quality.maxPageSidePx)
                    ?: return@forEachIndexed
                val (pageW, pageH) = resolvePageDimensions(
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    paperSize = config.paperSize,
                    orientation = config.orientation
                )
                val page = PDPage(PDRectangle(pageW.toFloat(), pageH.toFloat()))
                pdDoc.addPage(page)

                val margin = if (config.includeMargins && config.paperSize != PaperSize.ORIGINAL_RATIO) {
                    min(pageW, pageH) * 0.04f
                } else {
                    0f
                }
                val availW = max(1f, pageW - margin * 2f)
                val availH = max(1f, pageH - margin * 2f)
                val scale = min(availW / max(1, bitmap.width), availH / max(1, bitmap.height))
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                val left = (pageW - drawW) / 2f
                val bottom = (pageH - drawH) / 2f

                val pdImage = JPEGFactory.createFromImage(pdDoc, bitmap, config.quality.jpegQuality / 100f)
                PDPageContentStream(pdDoc, page).use { cs ->
                    cs.drawImage(pdImage, left, bottom, drawW, drawH)
                }
                bitmap.recycle()
                count++
            }
            destination.parentFile?.mkdirs()
            pdDoc.save(destination)
        }
        return count
    }

    internal fun resolvePageDimensions(
        bitmapWidth: Int,
        bitmapHeight: Int,
        paperSize: PaperSize,
        orientation: PdfOrientation
    ): Pair<Int, Int> {
        if (paperSize == PaperSize.ORIGINAL_RATIO) {
            val maxPt = 1000f
            val scale = min(maxPt / max(1, bitmapWidth), maxPt / max(1, bitmapHeight))
            val w = max(200, (bitmapWidth * scale).toInt())
            val h = max(200, (bitmapHeight * scale).toInt())
            return if (orientation == PdfOrientation.LANDSCAPE && h > w) h to w else w to h
        }

        val baseW = paperSize.widthPt
        val baseH = paperSize.heightPt
        return when (orientation) {
            PdfOrientation.PORTRAIT -> min(baseW, baseH) to max(baseW, baseH)
            PdfOrientation.LANDSCAPE -> max(baseW, baseH) to min(baseW, baseH)
        }
    }
}
