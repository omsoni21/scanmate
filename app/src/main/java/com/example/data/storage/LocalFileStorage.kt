package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.ImageQuality
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class LocalFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootDir: File by lazy { File(context.filesDir, "scanmate").apply { mkdirs() } }
    private val originalsDir: File by lazy { File(rootDir, "originals").apply { mkdirs() } }
    private val processedDir: File by lazy { File(rootDir, "processed").apply { mkdirs() } }
    private val pdfsDir: File by lazy { File(rootDir, "pdfs").apply { mkdirs() } }
    private val thumbsDir: File by lazy { File(rootDir, "thumbs").apply { mkdirs() } }

    fun ensurePdfBoxInitialized() {
        if (!PDFBoxResourceLoader.isReady()) {
            runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        }
    }

    fun createTempPdfFile(prefix: String = "tmp_pdf"): File =
        File(context.cacheDir, "${prefix}_${UUID.randomUUID().toString().take(8)}.pdf")

    fun createOriginalImageFile(): File =
        File(originalsDir, "orig_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

    fun createProcessedImageFile(): File =
        File(processedDir, "proc_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

    fun createPdfFile(preferredName: String): File {
        val safeName = preferredName
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .trim()
            .ifBlank { "ScanMate_${System.currentTimeMillis()}" }
        var candidate = File(pdfsDir, "$safeName.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(pdfsDir, "${safeName}_$counter.pdf")
            counter++
        }
        return candidate
    }

    fun createThumbnailFile(): File =
        File(thumbsDir, "thumb_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

    suspend fun importImageFromUri(uri: Uri, maxDimension: Int = 2400): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeUriWithExif(uri, maxDimension) ?: return@withContext null
            val outFile = createOriginalImageFile()
            saveBitmapJpeg(bitmap, outFile, 92)
            if (!bitmap.isRecycled) bitmap.recycle()
            outFile
        }.getOrNull()
    }

    suspend fun importPdfFromUri(uri: Uri, targetName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = createPdfFile(targetName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            outFile
        }.getOrNull()
    }

    suspend fun exportFileToUri(sourcePath: String, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(sourcePath)
            if (!source.exists()) return@withContext false
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    suspend fun copyFile(sourcePath: String, targetFile: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(sourcePath)
            if (!src.exists()) return@withContext null
            src.copyTo(targetFile, overwrite = true)
            targetFile
        }.getOrNull()
    }

    fun loadBitmap(path: String, maxDimension: Int = 2200): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        val exifAngle = readExifRotationDegrees(path)
        return if (exifAngle != 0) {
            val rotated = rotateBitmap(decoded, exifAngle)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }

    fun saveBitmapJpeg(bitmap: Bitmap, file: File, quality: Int = 90) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
        }
    }

    suspend fun saveThumbnail(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val file = createThumbnailFile()
        val maxSide = 480f
        val scale = minOf(maxSide / max(1, bitmap.width), maxSide / max(1, bitmap.height), 1f)
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        val thumb = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        saveBitmapJpeg(thumb, file, 82)
        if (thumb != bitmap) thumb.recycle()
        file
    }

    suspend fun saveProcessedImage(bitmap: Bitmap, quality: ImageQuality): File = withContext(Dispatchers.IO) {
        val file = createProcessedImageFile()
        saveBitmapJpeg(bitmap, file, quality.jpegQuality)
        file
    }

    fun deleteFileQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    suspend fun computeTotalStorageBytes(): Long = withContext(Dispatchers.IO) {
        rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getFileSize(path: String?): Long {
        if (path.isNullOrBlank()) return 0L
        val f = File(path)
        return if (f.exists()) f.length() else 0L
    }

    fun buildShareIntent(filePaths: List<String>, mimeType: String, title: String): Intent? {
        val uris = filePaths.mapNotNull { path ->
            val file = File(path)
            if (!file.exists()) null
            else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        if (uris.isEmpty()) return null

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(intent, title)
    }

    private fun decodeUriWithExif(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            exifOrientationToDegrees(
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            )
        } ?: 0

        return if (rotation != 0) {
            val rotated = rotateBitmap(bitmap, rotation)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }

    private fun readExifRotationDegrees(path: String): Int {
        return runCatching {
            val exif = ExifInterface(path)
            exifOrientationToDegrees(
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            )
        }.getOrDefault(0)
    }

    private fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        if (width <= 0 || height <= 0) return 1
        while (max(width / inSampleSize, height / inSampleSize) > maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
