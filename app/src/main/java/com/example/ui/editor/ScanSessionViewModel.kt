package com.example.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.FolderEntity
import com.example.data.power.BatteryStatusMonitor
import com.example.data.repository.ScanMateRepository
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.BatterySaverProfile
import com.example.domain.model.DocumentFilter
import com.example.domain.model.ImageQuality
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.model.PdfQuality
import com.example.domain.model.QuadPoints
import com.example.ui.session.ActiveScanSessionHolder
import com.example.ui.session.ScanSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportProgressState(
    val isExporting: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val errorMessage: String? = null
)

data class OcrSheetState(
    val selectedLanguage: OcrLanguage = OcrLanguage.ENGLISH,
    val isRunning: Boolean = false,
    val result: OcrResult? = null
)

@HiltViewModel
class ScanSessionViewModel @Inject constructor(
    private val repository: ScanMateRepository,
    private val fileStorage: LocalFileStorage,
    private val preferencesManager: PreferencesManager,
    private val sessionHolder: ActiveScanSessionHolder,
    batteryStatusMonitor: BatteryStatusMonitor
) : ViewModel() {

    val sessionState: StateFlow<ScanSessionState> = sessionHolder.state
    val folders: StateFlow<List<FolderEntity>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultPaperSize: StateFlow<PaperSize> = preferencesManager.defaultPaperSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaperSize.A4)

    val defaultPdfQuality: StateFlow<PdfQuality> = preferencesManager.defaultPdfQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PdfQuality.STANDARD)

    val batterySaverProfile: StateFlow<BatterySaverProfile> = batteryStatusMonitor.batterySaverProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatterySaverProfile())

    private val _exportState = MutableStateFlow(ExportProgressState())
    val exportState: StateFlow<ExportProgressState> = _exportState.asStateFlow()

    private val _ocrState = MutableStateFlow(OcrSheetState())
    val ocrState: StateFlow<OcrSheetState> = _ocrState.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureDefaultFoldersSeeded() }
    }

    fun setDocumentTitle(title: String) = sessionHolder.setTitle(title)
    fun setTargetFolder(folderId: String) = sessionHolder.setFolderId(folderId)

    fun movePage(fromIndex: Int, toIndex: Int) = sessionHolder.movePage(fromIndex, toIndex)
    fun deletePage(pageId: String) = sessionHolder.deletePage(pageId)

    fun rotatePage(pageId: String) {
        viewModelScope.launch {
            sessionHolder.rotatePageQuick(pageId)
        }
    }

    fun savePageCropAndRotation(
        pageId: String,
        quad: QuadPoints,
        rotationDegrees: Int,
        onDone: () -> Unit
    ) {
        val page = sessionHolder.getPage(pageId) ?: return
        viewModelScope.launch {
            sessionHolder.updatePageAndReprocess(
                pageId = pageId,
                quad = quad,
                rotationDegrees = rotationDegrees,
                filter = page.filter,
                brightness = page.brightness,
                contrast = page.contrast,
                sharpness = page.sharpness,
                quality = page.imageQuality
            )
            onDone()
        }
    }

    fun savePageEnhancement(
        pageId: String,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        rotationDegrees: Int,
        quality: ImageQuality,
        overwriteOriginal: Boolean,
        onDone: () -> Unit
    ) {
        val page = sessionHolder.getPage(pageId) ?: return
        viewModelScope.launch {
            sessionHolder.updatePageAndReprocess(
                pageId = pageId,
                quad = page.cropCorners,
                rotationDegrees = rotationDegrees,
                filter = filter,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                quality = quality,
                overwriteOriginal = overwriteOriginal
            )
            onDone()
        }
    }

    fun importMoreImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val filter = preferencesManager.defaultFilter.first()
            for (uri in uris) {
                val file = fileStorage.importImageFromUri(uri)
                if (file != null) {
                    sessionHolder.addCapturedPage(file, filter)
                }
            }
        }
    }

    fun setOcrLanguage(lang: OcrLanguage) {
        _ocrState.update { it.copy(selectedLanguage = lang, result = null) }
    }

    fun clearOcrResult() {
        _ocrState.update { it.copy(result = null) }
    }

    fun runOcrForPage(pageId: String) {
        val page = sessionHolder.getPage(pageId) ?: return
        viewModelScope.launch {
            _ocrState.update { it.copy(isRunning = true, result = null) }
            val result = repository.runOcrOnImagePath(page.processedImagePath, _ocrState.value.selectedLanguage)
            if (result is OcrResult.Success) {
                sessionHolder.updatePageOcrText(pageId, result.text)
            }
            _ocrState.update { it.copy(isRunning = false, result = result) }
        }
    }

    fun savePageOcrText(pageId: String, text: String) {
        sessionHolder.updatePageOcrText(pageId, text)
    }

    fun clearExportError() {
        _exportState.update { it.copy(errorMessage = null) }
    }

    fun exportSession(
        config: PdfGenerationConfig,
        exportAsPdf: Boolean,
        onSaved: (documentId: Long) -> Unit
    ) {
        val current = sessionHolder.state.value
        if (current.pages.isEmpty()) return
        viewModelScope.launch {
            _exportState.value = ExportProgressState(isExporting = true, currentPage = 0, totalPages = current.pages.size)
            try {
                val docId = repository.saveDocumentFromDrafts(
                    existingDocumentId = current.editingDocumentId,
                    drafts = current.pages,
                    config = config,
                    exportAsPdf = exportAsPdf,
                    onProgress = { cur, tot ->
                        _exportState.value = ExportProgressState(isExporting = true, currentPage = cur, totalPages = tot)
                    }
                )
                sessionHolder.clearSession()
                _exportState.value = ExportProgressState()
                onSaved(docId)
            } catch (e: Exception) {
                _exportState.value = ExportProgressState(
                    isExporting = false,
                    errorMessage = e.localizedMessage ?: "Failed to generate PDF."
                )
            }
        }
    }
}
