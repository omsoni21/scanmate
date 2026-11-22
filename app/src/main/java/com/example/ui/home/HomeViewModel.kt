package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.PageEntity
import com.example.data.power.BatteryStatusMonitor
import com.example.data.repository.ScanMateRepository
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.BatterySaverProfile
import com.example.ui.session.ActiveScanSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val totalDocuments: Int = 0,
    val totalPdfFiles: Int = 0,
    val totalStorageBytes: Long = 0L,
    val favoriteDocuments: List<DocumentEntity> = emptyList(),
    val favoriteFolders: List<FolderEntity> = emptyList(),
    val folderDocumentCounts: Map<String, Int> = emptyMap(),
    val recentDocuments: List<DocumentEntity> = emptyList(),
    val recentPages: List<PageEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val isImporting: Boolean = false,
    val batterySaver: BatterySaverProfile = BatterySaverProfile()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScanMateRepository,
    private val fileStorage: LocalFileStorage,
    private val preferencesManager: PreferencesManager,
    private val scanSessionHolder: ActiveScanSessionHolder,
    batteryStatusMonitor: BatteryStatusMonitor
) : ViewModel() {

    private val storageBytes = MutableStateFlow(0L)
    private val isImporting = MutableStateFlow(false)

    private val statusFlow = combine(
        storageBytes,
        isImporting,
        batteryStatusMonitor.batterySaverProfile
    ) { bytes, importing, powerProfile ->
        Triple(bytes, importing, powerProfile)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.documents,
        repository.recentPages,
        repository.folders,
        statusFlow
    ) { docs, pages, folders, status ->
        val (bytes, importing, powerProfile) = status
        HomeUiState(
            totalDocuments = docs.size,
            totalPdfFiles = docs.count { it.fileType == "PDF" },
            totalStorageBytes = bytes,
            favoriteDocuments = docs.filter { it.isFavorite },
            favoriteFolders = folders.filter { it.isFavorite },
            folderDocumentCounts = docs.groupingBy { it.folderId }.eachCount(),
            recentDocuments = docs.take(6),
            recentPages = pages.take(10),
            folders = folders,
            isImporting = importing,
            batterySaver = powerProfile
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultFoldersSeeded()
            refreshStorage()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            storageBytes.value = repository.getTotalStorageBytes()
        }
    }

    fun startNewSession(folderId: String = "personal") {
        scanSessionHolder.startFreshSession(folderId = folderId)
    }

    fun importImagesIntoNewSession(uris: List<Uri>, onReady: () -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImporting.value = true
            try {
                scanSessionHolder.startFreshSession()
                val filter = preferencesManager.defaultFilter.first()
                for (uri in uris) {
                    val file = fileStorage.importImageFromUri(uri)
                    if (file != null) {
                        scanSessionHolder.addCapturedPage(file, filter)
                    }
                }
                refreshStorage()
                onReady()
            } finally {
                isImporting.value = false
            }
        }
    }

    fun importExternalPdf(uri: Uri, title: String, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            isImporting.value = true
            try {
                val id = repository.importExternalPdf(uri, title, "personal")
                refreshStorage()
                if (id != null) onImported(id)
            } finally {
                isImporting.value = false
            }
        }
    }

    fun createFolder(name: String, colorHex: Long, iconKey: String) {
        viewModelScope.launch {
            repository.createFolder(name, colorHex, iconKey)
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(documentId, newTitle)
        }
    }

    fun moveDocument(documentId: Long, folderId: String) {
        viewModelScope.launch {
            repository.moveDocumentsToFolder(listOf(documentId), folderId)
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            repository.deleteDocuments(listOf(documentId))
            refreshStorage()
        }
    }

    fun toggleDocumentFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.setDocumentFavorite(document.id, !document.isFavorite)
        }
    }

    fun toggleFolderFavorite(folder: FolderEntity) {
        viewModelScope.launch {
            repository.setFolderFavorite(folder.id, !folder.isFavorite)
        }
    }

    fun editDocumentPages(document: DocumentEntity, onLoaded: () -> Unit) {
        viewModelScope.launch {
            val drafts = repository.loadDraftsForDocument(document.id)
            if (drafts.isNotEmpty()) {
                scanSessionHolder.startEditingDocument(
                    documentId = document.id,
                    title = document.title,
                    folderId = document.folderId,
                    pages = drafts
                )
                onLoaded()
            }
        }
    }

    fun buildShareIntent(document: DocumentEntity): Intent? {
        val path = document.pdfPath ?: document.thumbnailPath ?: return null
        val mime = if (document.pdfPath != null) "application/pdf" else "image/jpeg"
        return fileStorage.buildShareIntent(listOf(path), mime, document.title)
    }
}
