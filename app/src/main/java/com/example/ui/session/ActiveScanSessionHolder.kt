package com.example.ui.session

import com.example.data.power.BatteryStatusMonitor
import com.example.data.processing.DocumentEdgeDetector
import com.example.data.processing.ImageProcessor
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.DocumentFilter
import com.example.domain.model.ImageQuality
import com.example.domain.model.QuadPoints
import com.example.domain.model.ScanDraftPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ScanSessionState(
    val editingDocumentId: Long? = null,
    val documentTitle: String = defaultScanName(),
    val targetFolderId: String = "personal",
    val pages: List<ScanDraftPage> = emptyList(),
    val isProcessing: Boolean = false
) {
    companion object {
        fun defaultScanName(): String {
            val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
            return "ScanMate_${fmt.format(Date())}"
        }
    }
}

@Singleton
class ActiveScanSessionHolder @Inject constructor(
    private val fileStorage: LocalFileStorage,
    private val batteryStatusMonitor: BatteryStatusMonitor
) {
    private val _state = MutableStateFlow(ScanSessionState())
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    fun startFreshSession(folderId: String = "personal", defaultTitle: String = ScanSessionState.defaultScanName()) {
        _state.value = ScanSessionState(
            editingDocumentId = null,
            documentTitle = defaultTitle,
            targetFolderId = folderId,
            pages = emptyList()
        )
    }

    fun startEditingDocument(
        documentId: Long,
        title: String,
        folderId: String,
        pages: List<ScanDraftPage>
    ) {
        _state.value = ScanSessionState(
            editingDocumentId = documentId,
            documentTitle = title,
            targetFolderId = folderId,
            pages = pages
        )
    }

    fun startCombinedSession(
        title: String,
        folderId: String,
        pages: List<ScanDraftPage>
    ) {
        _state.value = ScanSessionState(
            editingDocumentId = null,
            documentTitle = title,
            targetFolderId = folderId,
            pages = pages
        )
    }

    fun setTitle(title: String) {
        _state.update { it.copy(documentTitle = title) }
    }

    fun setFolderId(folderId: String) {
        _state.update { it.copy(targetFolderId = folderId) }
    }

    suspend fun addCapturedPage(
        originalFile: File,
        defaultFilter: DocumentFilter,
        preDetectedQuad: QuadPoints? = null
    ): ScanDraftPage = withContext(Dispatchers.IO) {
        val powerProfile = batteryStatusMonitor.currentProfile()
        val bitmap = fileStorage.loadBitmap(originalFile.absolutePath, powerProfile.maxCaptureInputDimensionPx)
        val detection = if (bitmap != null) {
            if (preDetectedQuad != null) {
                val res = DocumentEdgeDetector.detectFromBitmap(bitmap)
                if (res.isAutoDetected) res else res.copy(quad = preDetectedQuad)
            } else {
                DocumentEdgeDetector.detectFromBitmap(bitmap)
            }
        } else null

        val quad = detection?.quad ?: QuadPoints.DEFAULT_INSET
        val autoDetected = detection?.isAutoDetected ?: false
        val quality = powerProfile.scanQuality
        val sharpness = powerProfile.defaultSharpness

        val processedFile = if (bitmap != null) {
            val processed = ImageProcessor.processFullPipeline(
                originalBitmap = bitmap,
                quad = quad,
                rotationDegrees = 0,
                filter = defaultFilter,
                brightness = 0f,
                contrast = 1f,
                sharpness = sharpness,
                quality = quality,
                maxDimensionOverridePx = powerProfile.maxScanDimensionPx,
                enhancementIntensity = powerProfile.enhancementIntensity
            )
            val saved = fileStorage.saveProcessedImage(processed, quality)
            processed.recycle()
            bitmap.recycle()
            saved
        } else {
            fileStorage.copyFile(originalFile.absolutePath, fileStorage.createProcessedImageFile())
                ?: originalFile
        }

        val page = ScanDraftPage(
            originalImagePath = originalFile.absolutePath,
            processedImagePath = processedFile.absolutePath,
            filter = defaultFilter,
            sharpness = sharpness,
            cropCorners = quad,
            imageQuality = quality,
            edgeAutoDetected = autoDetected
        )
        _state.update { it.copy(pages = it.pages + page) }
        page
    }

    fun getPage(pageId: String): ScanDraftPage? = _state.value.pages.find { it.id == pageId }

    suspend fun updatePageAndReprocess(
        pageId: String,
        quad: QuadPoints,
        rotationDegrees: Int,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        quality: ImageQuality,
        overwriteOriginal: Boolean = false
    ): ScanDraftPage? = withContext(Dispatchers.IO) {
        val current = getPage(pageId) ?: return@withContext null
        val powerProfile = batteryStatusMonitor.currentProfile()
        val effectiveMaxDim = if (powerProfile.isActive) {
            minOf(quality.maxDimensionPx, powerProfile.maxScanDimensionPx)
        } else {
            quality.maxDimensionPx
        }
        val origBitmap = fileStorage.loadBitmap(current.originalImagePath, effectiveMaxDim)
            ?: return@withContext null

        val processedBitmap = ImageProcessor.processFullPipeline(
            originalBitmap = origBitmap,
            quad = quad,
            rotationDegrees = rotationDegrees,
            filter = filter,
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            quality = quality,
            maxDimensionOverridePx = effectiveMaxDim,
            enhancementIntensity = powerProfile.enhancementIntensity
        )
        val newProcessedFile = fileStorage.saveProcessedImage(processedBitmap, quality)

        var newOriginalPath = current.originalImagePath
        var finalQuad = quad
        var finalRotation = rotationDegrees
        if (overwriteOriginal) {
            val overwritten = File(current.originalImagePath)
            fileStorage.saveBitmapJpeg(processedBitmap, overwritten, quality.jpegQuality)
            newOriginalPath = overwritten.absolutePath
            finalQuad = QuadPoints.FULL_FRAME
            finalRotation = 0
        }

        processedBitmap.recycle()
        origBitmap.recycle()

        if (current.processedImagePath != newProcessedFile.absolutePath &&
            current.processedImagePath != current.originalImagePath
        ) {
            fileStorage.deleteFileQuietly(current.processedImagePath)
        }

        val updated = current.copy(
            originalImagePath = newOriginalPath,
            processedImagePath = newProcessedFile.absolutePath,
            cropCorners = finalQuad,
            rotationDegrees = finalRotation,
            filter = filter,
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            imageQuality = quality
        )

        _state.update { state ->
            state.copy(pages = state.pages.map { if (it.id == pageId) updated else it })
        }
        updated
    }

    suspend fun rotatePageQuick(pageId: String) {
        val page = getPage(pageId) ?: return
        val nextRot = (page.rotationDegrees + 90) % 360
        updatePageAndReprocess(
            pageId = pageId,
            quad = page.cropCorners,
            rotationDegrees = nextRot,
            filter = page.filter,
            brightness = page.brightness,
            contrast = page.contrast,
            sharpness = page.sharpness,
            quality = page.imageQuality
        )
    }

    fun updatePageOcrText(pageId: String, text: String) {
        _state.update { state ->
            state.copy(pages = state.pages.map { if (it.id == pageId) it.copy(ocrText = text) else it })
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _state.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = state.pages.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(pages = mutable)
        }
    }

    fun deletePage(pageId: String) {
        _state.update { state ->
            state.copy(pages = state.pages.filterNot { it.id == pageId })
        }
    }

    fun clearSession() {
        _state.value = ScanSessionState()
    }
}
