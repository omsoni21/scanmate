package com.example.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.repository.ScanMateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderWithStats(
    val folder: FolderEntity,
    val documentCount: Int,
    val totalSizeBytes: Long
)

data class FoldersUiState(
    val folders: List<FolderWithStats> = emptyList(),
    val searchQuery: String = ""
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val repository: ScanMateRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<FoldersUiState> = combine(
        repository.folders,
        repository.documents,
        searchQuery
    ) { folders, docs, query ->
        val filtered = folders.filter { f ->
            query.isBlank() || f.name.contains(query, ignoreCase = true)
        }
        val grouped = docs.groupBy { it.folderId }
        val stats = filtered.map { folder ->
            val folderDocs = grouped[folder.id].orEmpty()
            FolderWithStats(
                folder = folder,
                documentCount = folderDocs.size,
                totalSizeBytes = folderDocs.sumOf { it.fileSizeBytes }
            )
        }
        FoldersUiState(folders = stats, searchQuery = query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FoldersUiState()
    )

    fun setSearchQuery(q: String) { searchQuery.value = q }

    fun observeFolder(folderId: String): Flow<FolderEntity?> = repository.observeFolder(folderId)

    fun observeDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>> =
        repository.observeDocumentsInFolder(folderId)

    fun createFolder(name: String, colorHex: Long, iconKey: String) {
        viewModelScope.launch { repository.createFolder(name, colorHex, iconKey) }
    }

    fun renameFolder(folder: FolderEntity, newName: String) {
        viewModelScope.launch { repository.renameFolder(folder, newName) }
    }

    fun updateFolderAppearance(folder: FolderEntity, colorHex: Long, iconKey: String) {
        viewModelScope.launch { repository.updateFolderAppearance(folder, colorHex, iconKey) }
    }

    fun deleteCustomFolder(folderId: String) {
        viewModelScope.launch { repository.deleteCustomFolder(folderId) }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch { repository.renameDocument(documentId, newTitle) }
    }

    fun moveDocument(documentId: Long, targetFolderId: String) {
        viewModelScope.launch { repository.moveDocumentsToFolder(listOf(documentId), targetFolderId) }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch { repository.deleteDocuments(listOf(documentId)) }
    }

    fun toggleFolderFavorite(folder: FolderEntity) {
        viewModelScope.launch {
            repository.setFolderFavorite(folder.id, !folder.isFavorite)
        }
    }

    fun toggleDocumentFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.setDocumentFavorite(document.id, !document.isFavorite)
        }
    }
}
