package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

data class PdfMetadataInfo(
    val pageCount: Int,
    val pageWidthPt: Int,
    val pageHeightPt: Int,
    val isEncrypted: Boolean = false
)

sealed interface PdfInspectOutcome {
    data class Ready(val info: PdfMetadataInfo) : PdfInspectOutcome
    data object PasswordRequired : PdfInspectOutcome
    data object WrongPassword : PdfInspectOutcome
    data object Error : PdfInspectOutcome
}

@Singleton
class PdfRendererHelper @Inject constructor() {

    private val renderMutex = Mutex()

    suspend fun inspectPdfOutcome(
        pdfPath: String,
        password: String? = null
    ): PdfInspectOutcome = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val file = File(pdfPath)
            if (!file.exists()) return@withLock PdfInspectOutcome.Error

            // Try PDFBox first when checking encryption or unlocking reliably across all API levels (24..36)
            try {
                val pdDoc = if (password != null) {
                    PDDocument.load(file, password)
                } else {
                    PDDocument.load(file)
                }
                pdDoc.use { doc ->
                    val count = doc.numberOfPages
                    var w = 0
                    var h = 0
                    if (count > 0) {
                        val box = doc.getPage(0).mediaBox
                        w = box.width.roundToInt()
                        h = box.height.roundToInt()
                    }
                    return@withLock PdfInspectOutcome.Ready(
                        PdfMetadataInfo(
                            pageCount = count,
                            pageWidthPt = w,
                            pageHeightPt = h,
                            isEncrypted = doc.isEncrypted
                        )
                    )
                }
            } catch (_: InvalidPasswordException) {
                return@withLock if (password == null) {
                    PdfInspectOutcome.PasswordRequired
                } else {
                    PdfInspectOutcome.WrongPassword
                }
            } catch (_: Throwable) {
                // Fall back to Android PdfRenderer inspection
            }

            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    openPlatformRenderer(pfd, password).use { renderer ->
                        val count = renderer.pageCount
                        var w = 0
                        var h = 0
                        if (count > 0) {
                            renderer.openPage(0).use { p ->
                                w = p.width
                                h = p.height
                            }
                        }
                        PdfInspectOutcome.Ready(
                            PdfMetadataInfo(
                                pageCount = count,
                                pageWidthPt = w,
                                pageHeightPt = h,
                                isEncrypted = password != null
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                if (password == null) PdfInspectOutcome.PasswordRequired else PdfInspectOutcome.WrongPassword
            } catch (_: Throwable) {
                PdfInspectOutcome.Error
            }
        }
    }

    suspend fun inspectPdf(pdfPath: String, password: String? = null): PdfMetadataInfo? {
        return when (val outcome = inspectPdfOutcome(pdfPath, password)) {
            is PdfInspectOutcome.Ready -> outcome.info
            else -> null
        }
    }

    suspend fun renderPage(
        pdfPath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1400,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val file = File(pdfPath)
            if (!file.exists()) return@withLock null

            // Attempt native PdfRenderer directly (supports LoadParams with password on API 35+)
            if (password == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val direct = renderWithPlatformRenderer(file, pageIndex, targetWidthPx, password)
                if (direct != null) return@withLock direct
            }

            // Fallback for API 24..34 encrypted PDFs (or if platform LoadParams didn't accept scheme)
            if (password != null) {
                val tempUnlocked = File.createTempFile("sm_unlock_", ".pdf")
                try {
                    PDDocument.load(file, password).use { doc ->
                        doc.isAllSecurityToBeRemoved = true
                        doc.save(tempUnlocked)
                    }
                    return@withLock renderWithPlatformRenderer(tempUnlocked, pageIndex, targetWidthPx, null)
                } catch (_: Throwable) {
                    return@withLock null
                } finally {
                    tempUnlocked.delete()
                }
            }

            null
        }
    }

    private fun renderWithPlatformRenderer(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int,
        password: String?
    ): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            openPlatformRenderer(pfd, password).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
                renderer.openPage(pageIndex).use { page ->
                    val aspect = page.height.toFloat() / max(1, page.width)
                    val w = targetWidthPx.coerceIn(240, 2400)
                    val h = max(240, (w * aspect).roundToInt())
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.getOrNull()

    private fun openPlatformRenderer(pfd: ParcelFileDescriptor, password: String?): PdfRenderer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && password != null) {
            val params = LoadParams.Builder()
                .setPassword(password)
                .build()
            PdfRenderer(pfd, params)
        } else {
            PdfRenderer(pfd)
        }
    }
}
