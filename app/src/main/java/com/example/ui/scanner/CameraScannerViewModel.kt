package com.example.ui.scanner

import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.power.BatteryStatusMonitor
import com.example.data.processing.DocumentEdgeDetector
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.BatterySaverProfile
import com.example.domain.model.QuadPoints
import com.example.ui.session.ActiveScanSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class CameraUiState(
    val detectedQuad: QuadPoints? = null,
    val confidence: Float = 0f,
    val stableFrameCount: Int = 0,
    val hasTextDetected: Boolean = false,
    val textBlockCount: Int = 0,
    val detectedTextSnippet: String? = null,
    val isTorchOn: Boolean = false,
    val useFrontCamera: Boolean = false,
    val isCapturing: Boolean = false,
    val capturedPageCount: Int = 0,
    val latestCapturedPageId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class CameraScannerViewModel @Inject constructor(
    private val fileStorage: LocalFileStorage,
    private val preferencesManager: PreferencesManager,
    private val scanSessionHolder: ActiveScanSessionHolder,
    batteryStatusMonitor: BatteryStatusMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val autoCaptureEnabled: StateFlow<Boolean> = preferencesManager.autoCaptureEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gridOverlayEnabled: StateFlow<Boolean> = preferencesManager.gridOverlayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val batterySaverProfile: StateFlow<BatterySaverProfile> = batteryStatusMonitor.batterySaverProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatterySaverProfile())

    val sessionPages = scanSessionHolder.state

    private var lastDetectedQuad: QuadPoints? = null
    private var lastAutoTriggerMillis: Long = 0L
    private var lastAnalyzedTimestampMs: Long = 0L

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private var isAnalyzingText = false
    private var lastExtractedText = ""
    private var stableTextCount = 0

    fun prepareSessionIfNeeded(folderId: String, appendToCurrent: Boolean) {
        if (!appendToCurrent) {
            scanSessionHolder.startFreshSession(folderId = folderId)
        }
        _uiState.update { it.copy(capturedPageCount = scanSessionHolder.state.value.pages.size) }
    }

    fun createCaptureOutputFile(): File = fileStorage.createOriginalImageFile()

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun toggleCameraFacing() {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera, isTorchOn = false) }
    }

    fun toggleGrid() {
        viewModelScope.launch {
            preferencesManager.setGridOverlayEnabled(!gridOverlayEnabled.value)
        }
    }

    fun toggleAutoCapture() {
        viewModelScope.launch {
            preferencesManager.setAutoCaptureEnabled(!autoCaptureEnabled.value)
        }
    }

    fun setCapturing(capturing: Boolean) {
        _uiState.update { it.copy(isCapturing = capturing) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    @Suppress("UnsafeOptInUsageError")
    fun analyzeFrame(imageProxy: ImageProxy, onShouldAutoCapture: () -> Unit) {
        val now = System.currentTimeMillis()
        val throttleMs = batterySaverProfile.value.analysisThrottleMs
        if (throttleMs > 0L && now - lastAnalyzedTimestampMs < throttleMs) {
            return
        }
        lastAnalyzedTimestampMs = now

        val result = DocumentEdgeDetector.detectFromImageProxy(imageProxy)
        val quad = if (result.isAutoDetected) result.quad else null
        val prev = lastDetectedQuad

        val newStableCount = if (quad != null && prev != null && DocumentEdgeDetector.isStable(quad, prev)) {
            _uiState.value.stableFrameCount + 1
        } else {
            0
        }
        lastDetectedQuad = quad

        // Auto Text Recognition via ML Kit
        val mediaImage = imageProxy.image
        if (mediaImage != null && !isAnalyzingText) {
            isAnalyzingText = true
            val rotation = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val cleanText = visionText.text.trim()
                    val textBlocks = visionText.textBlocks
                    val hasText = cleanText.length >= 3 && textBlocks.isNotEmpty()
                    val snippet = textBlocks.firstOrNull()?.text?.lines()?.firstOrNull { it.isNotBlank() }?.take(35)

                    if (hasText) {
                        lastExtractedText = cleanText
                        stableTextCount++
                    } else {
                        stableTextCount = 0
                    }

                    _uiState.update { current ->
                        current.copy(
                            hasTextDetected = hasText,
                            textBlockCount = textBlocks.size,
                            detectedTextSnippet = if (hasText) snippet else null
                        )
                    }
                }
                .addOnCompleteListener {
                    isAnalyzingText = false
                }
        }

        _uiState.update {
            it.copy(
                detectedQuad = quad,
                confidence = result.confidence,
                stableFrameCount = newStableCount
            )
        }

        val isEdgeReady = quad != null && newStableCount >= 4
        val isTextReady = _uiState.value.hasTextDetected && stableTextCount >= 4

        if (autoCaptureEnabled.value &&
            (isEdgeReady || isTextReady) &&
            !_uiState.value.isCapturing &&
            now - lastAutoTriggerMillis > 2500L
        ) {
            lastAutoTriggerMillis = now
            onShouldAutoCapture()
        }
    }

    fun onPhotoCaptured(file: File, onPageProcessed: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true) }
            try {
                val filter = preferencesManager.defaultFilter.first()
                val page = scanSessionHolder.addCapturedPage(
                    originalFile = file,
                    defaultFilter = filter,
                    preDetectedQuad = _uiState.value.detectedQuad
                )
                if (lastExtractedText.isNotBlank()) {
                    scanSessionHolder.updatePageOcrText(page.id, lastExtractedText)
                }
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        capturedPageCount = scanSessionHolder.state.value.pages.size,
                        latestCapturedPageId = page.id
                    )
                }
                onPageProcessed(page.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCapturing = false, errorMessage = e.localizedMessage ?: "Capture failed")
                }
            }
        }
    }

    fun importGalleryUris(uris: List<Uri>, onImported: () -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true) }
            try {
                val filter = preferencesManager.defaultFilter.first()
                for (uri in uris) {
                    val file = fileStorage.importImageFromUri(uri)
                    if (file != null) {
                        scanSessionHolder.addCapturedPage(file, filter)
                    }
                }
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        capturedPageCount = scanSessionHolder.state.value.pages.size
                    )
                }
                onImported()
            } finally {
                _uiState.update { it.copy(isCapturing = false) }
            }
        }
    }
}
