package com.example.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.PageEntity
import com.example.data.pdf.PdfInspectOutcome
import com.example.data.pdf.PdfRendererHelper
import com.example.data.repository.ScanMateRepository
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.example.ui.session.ActiveScanSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val document: DocumentEntity? = null,
    val folder: FolderEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val pageCount: Int = 0,
    val renderedBitmaps: Map<Int, Bitmap> = emptyMap(),
    val isLoading: Boolean = true,
    val requiresPassword: Boolean = false,
    val isUnlocking: Boolean = false,
    val passwordError: String? = null
)

data class ViewerOcrState(
    val selectedLanguage: OcrLanguage = OcrLanguage.ENGLISH,
    val isRunning: Boolean = false,
    val result: OcrResult? = null
)

private data class ViewerLockState(
    val requiresPassword: Boolean = false,
    val isUnlocking: Boolean = false,
    val passwordError: String? = null
)

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val repository: ScanMateRepository,
    private val pdfRendererHelper: PdfRendererHelper,
    private val fileStorage: LocalFileStorage,
    private val sessionHolder: ActiveScanSessionHolder
) : ViewModel() {

    private val renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    private val totalPages = MutableStateFlow(0)
    private val isLoading = MutableStateFlow(true)
    private val lockState = MutableStateFlow(ViewerLockState())

    private val _ocrState = MutableStateFlow(ViewerOcrState())
    val ocrState: StateFlow<ViewerOcrState> = _ocrState.asStateFlow()

    private var boundDocId: Long = -1L
    private var unlockedPassword: String? = null

    fun observeDocumentState(documentId: Long): StateFlow<ViewerUiState> {
        if (boundDocId != documentId) {
            boundDocId = documentId
            unlockedPassword = null
            renderedPages.value = emptyMap()
            lockState.value = ViewerLockState()
            loadContent(documentId)
        }
        val contentMetaFlow = combine(
            renderedPages,
            totalPages,
            isLoading,
            lockState
        ) { bitmaps, count, loading, lock ->
            ViewerContentMeta(bitmaps, count, loading, lock)
        }
        return combine(
            repository.observeDocument(documentId),
            repository.folders,
            repository.observePages(documentId),
            contentMetaFlow
        ) { doc, folders, pages, meta ->
            ViewerUiState(
                document = doc,
                folder = folders.find { it.id == doc?.folderId },
                pages = pages,
                pageCount = meta.count.coerceAtLeast(pages.size),
                renderedBitmaps = meta.bitmaps,
                isLoading = meta.loading,
                requiresPassword = meta.lock.requiresPassword,
                isUnlocking = meta.lock.isUnlocking,
                passwordError = meta.lock.passwordError
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewerUiState())
    }

    private data class ViewerContentMeta(
        val bitmaps: Map<Int, Bitmap>,
        val count: Int,
        val loading: Boolean,
        val lock: ViewerLockState
    )

    private fun loadContent(documentId: Long) {
        viewModelScope.launch {
            isLoading.value = true
            val entity = repository.observeDocument(documentId).first()
            if (entity == null) {
                isLoading.value = false
                return@launch
            }

            val pdfPath = entity.pdfPath
            if (!pdfPath.isNullOrBlank()) {
                when (val outcome = pdfRendererHelper.inspectPdfOutcome(pdfPath, unlockedPassword)) {
                    is PdfInspectOutcome.Ready -> {
                        val count = outcome.info.pageCount
                        totalPages.value = count
                        lockState.value = ViewerLockState(requiresPassword = false)
                        isLoading.value = false
                        for (i in 0 until count) {
                            if (!renderedPages.value.containsKey(i)) {
                                val bmp = pdfRendererHelper.renderPage(pdfPath, i, 1440, unlockedPassword)
                                if (bmp != null) {
                                    renderedPages.update { it + (i to bmp) }
                                }
                            }
                        }
                    }
                    PdfInspectOutcome.PasswordRequired, PdfInspectOutcome.WrongPassword -> {
                        totalPages.value = entity.pageCount.coerceAtLeast(1)
                        lockState.value = ViewerLockState(
                            requiresPassword = true,
                            isUnlocking = false,
                            passwordError = if (outcome == PdfInspectOutcome.WrongPassword) {
                                "Incorrect password. Please try again."
                            } else null
                        )
                        isLoading.value = false
                    }
                    PdfInspectOutcome.Error -> {
                        totalPages.value = entity.pageCount
                        isLoading.value = false
                    }
                }
            } else {
                val drafts = repository.loadDraftsForDocument(documentId)
                totalPages.value = drafts.size
                isLoading.value = false
                drafts.forEachIndexed { idx, p ->
                    if (!renderedPages.value.containsKey(idx)) {
                        val bmp = fileStorage.loadBitmap(p.processedImagePath, 1440)
                        if (bmp != null) {
                            renderedPages.update { it + (idx to bmp) }
                        }
                    }
                }
            }
        }
    }

    fun unlockWithPassword(document: DocumentEntity, password: String) {
        val pdfPath = document.pdfPath ?: return
        if (password.isEmpty()) {
            lockState.update { it.copy(passwordError = "Enter the PDF password.") }
            return
        }
        viewModelScope.launch {
            lockState.update { it.copy(isUnlocking = true, passwordError = null) }
            when (val outcome = pdfRendererHelper.inspectPdfOutcome(pdfPath, password)) {
                is PdfInspectOutcome.Ready -> {
                    unlockedPassword = password
                    val count = outcome.info.pageCount
                    totalPages.value = count
                    lockState.value = ViewerLockState(requiresPassword = false, isUnlocking = false)
                    val firstBmp = pdfRendererHelper.renderPage(pdfPath, 0, 1440, password)
                    if (firstBmp != null) {
                        renderedPages.update { it + (0 to firstBmp) }
                    }
                    for (i in 1 until count) {
                        if (!renderedPages.value.containsKey(i)) {
                            val bmp = pdfRendererHelper.renderPage(pdfPath, i, 1440, password)
                            if (bmp != null) {
                                renderedPages.update { it + (i to bmp) }
                            }
                        }
                    }
                }
                PdfInspectOutcome.WrongPassword, PdfInspectOutcome.PasswordRequired -> {
                    lockState.update {
                        it.copy(
                            requiresPassword = true,
                            isUnlocking = false,
                            passwordError = "Incorrect password. Please try again."
                        )
                    }
                }
                PdfInspectOutcome.Error -> {
                    lockState.update {
                        it.copy(
                            isUnlocking = false,
                            passwordError = "Unable to open this PDF file."
                        )
                    }
                }
            }
        }
    }

    fun protectWithPassword(documentId: Long, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.protectPdfDocument(documentId, password)
            if (ok) {
                unlockedPassword = password
            }
            onDone(ok)
        }
    }

    fun ensurePageRendered(document: DocumentEntity, pageIndex: Int) {
        if (lockState.value.requiresPassword || renderedPages.value.containsKey(pageIndex)) return
        viewModelScope.launch {
            val pdfPath = document.pdfPath
            if (!pdfPath.isNullOrBlank()) {
                val bmp = pdfRendererHelper.renderPage(pdfPath, pageIndex, 1440, unlockedPassword)
                if (bmp != null) {
                    renderedPages.update { it + (pageIndex to bmp) }
                }
            }
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch { repository.renameDocument(documentId, newTitle) }
    }

    fun deleteDocument(documentId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteDocuments(listOf(documentId))
            onDeleted()
        }
    }

    fun exportPdfToUri(document: DocumentEntity, destUri: Uri, onDone: (Boolean) -> Unit) {
        val path = document.pdfPath ?: return
        viewModelScope.launch {
            val ok = fileStorage.exportFileToUri(path, destUri)
            onDone(ok)
        }
    }

    fun buildShareIntent(document: DocumentEntity): Intent? {
        val path = document.pdfPath ?: document.thumbnailPath ?: return null
        val mime = if (document.pdfPath != null) "application/pdf" else "image/jpeg"
        return fileStorage.buildShareIntent(listOf(path), mime, document.title)
    }

    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.setDocumentFavorite(document.id, !document.isFavorite)
        }
    }

    fun editPagesInSession(document: DocumentEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            val drafts = repository.loadDraftsForDocument(document.id)
            if (drafts.isNotEmpty()) {
                sessionHolder.startEditingDocument(
                    documentId = document.id,
                    title = document.title,
                    folderId = document.folderId,
                    pages = drafts
                )
                onReady()
            }
        }
    }

    fun setOcrLanguage(lang: OcrLanguage) {
        _ocrState.update { it.copy(selectedLanguage = lang, result = null) }
    }

    fun runOcr(documentId: Long) {
        viewModelScope.launch {
            _ocrState.update { it.copy(isRunning = true, result = null) }
            val result = repository.runOcrOnPdfDocument(
                documentId = documentId,
                language = _ocrState.value.selectedLanguage,
                password = unlockedPassword
            )
            _ocrState.update { it.copy(isRunning = false, result = result) }
        }
    }

    fun saveOcrText(documentId: Long, text: String) {
        viewModelScope.launch {
            repository.saveDocumentOcrText(documentId, text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderedPages.value.values.forEach { if (!it.isRecycled) it.recycle() }
    }
}
