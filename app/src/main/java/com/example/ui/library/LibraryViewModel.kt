package com.example.ui.library

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.repository.ScanMateRepository
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.BulkRenameFormatter
import com.example.domain.model.DateRangeFilter
import com.example.domain.model.DocumentLibraryFilterEngine
import com.example.domain.model.FileTypeFilter
import com.example.domain.model.RenameSeparator
import com.example.domain.model.SortOption
import com.example.domain.model.SuffixPadding
import com.example.ui.session.ActiveScanSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.ALL,
    val selectedFolderId: String? = null,
    val dateRangeFilter: DateRangeFilter = DateRangeFilter.ALL,
    val customDateMillis: Long? = null,
    val favoritesOnly: Boolean = false,
    val isGridView: Boolean = false,
    val selectedDocumentIds: Set<Long> = emptySet(),
    val isPreparingMerge: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedDocumentIds.isNotEmpty()
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
            fileTypeFilter != FileTypeFilter.ALL ||
            selectedFolderId != null ||
            dateRangeFilter != DateRangeFilter.ALL ||
            favoritesOnly
}

private data class LibraryQueryCriteria(
    val query: String,
    val sortOption: SortOption,
    val fileTypeFilter: FileTypeFilter,
    val folderId: String?,
    val dateRangeFilter: DateRangeFilter,
    val customDateMillis: Long?,
    val favoritesOnly: Boolean
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: ScanMateRepository,
    private val fileStorage: LocalFileStorage,
    private val sessionHolder: ActiveScanSessionHolder
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val fileTypeFilter = MutableStateFlow(FileTypeFilter.ALL)
    private val selectedFolderId = MutableStateFlow<String?>(null)
    private val dateRangeFilter = MutableStateFlow(DateRangeFilter.ALL)
    private val customDateMillis = MutableStateFlow<Long?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val isGridView = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val isPreparingMerge = MutableStateFlow(false)

    private val dateAndFavoriteFlow = combine(
        dateRangeFilter,
        customDateMillis,
        favoritesOnly
    ) { range, customMillis, favOnly -> Triple(range, customMillis, favOnly) }

    private val criteriaFlow = combine(
        searchQuery,
        sortOption,
        fileTypeFilter,
        selectedFolderId,
        dateAndFavoriteFlow
    ) { query, sort, typeFilter, folderId, extra ->
        LibraryQueryCriteria(
            query = query,
            sortOption = sort,
            fileTypeFilter = typeFilter,
            folderId = folderId,
            dateRangeFilter = extra.first,
            customDateMillis = extra.second,
            favoritesOnly = extra.third
        )
    }

    private val viewOptionsFlow = combine(
        isGridView,
        selectedIds,
        isPreparingMerge
    ) { grid, selected, merging -> Triple(grid, selected, merging) }

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.documents,
        repository.folders,
        criteriaFlow,
        viewOptionsFlow
    ) { docs, folders, criteria, viewOpts ->
        val filteredAndSorted = DocumentLibraryFilterEngine.filterAndSort(
            documents = docs,
            query = criteria.query,
            fileTypeFilter = criteria.fileTypeFilter,
            folderId = criteria.folderId,
            dateRangeFilter = criteria.dateRangeFilter,
            customDateMillis = criteria.customDateMillis,
            sortOption = criteria.sortOption,
            favoritesOnly = criteria.favoritesOnly
        )

        LibraryUiState(
            documents = filteredAndSorted,
            folders = folders,
            searchQuery = criteria.query,
            sortOption = criteria.sortOption,
            fileTypeFilter = criteria.fileTypeFilter,
            selectedFolderId = criteria.folderId,
            dateRangeFilter = criteria.dateRangeFilter,
            customDateMillis = criteria.customDateMillis,
            favoritesOnly = criteria.favoritesOnly,
            isGridView = viewOpts.first,
            selectedDocumentIds = viewOpts.second,
            isPreparingMerge = viewOpts.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setSortOption(option: SortOption) { sortOption.value = option }
    fun setFileTypeFilter(filter: FileTypeFilter) { fileTypeFilter.value = filter }
    fun setFolderFilter(folderId: String?) { selectedFolderId.value = folderId }

    fun setDateRangeFilter(filter: DateRangeFilter) {
        dateRangeFilter.value = filter
        if (filter != DateRangeFilter.CUSTOM_DATE) {
            customDateMillis.value = null
        }
    }

    fun setCustomDate(millis: Long) {
        customDateMillis.value = millis
        dateRangeFilter.value = DateRangeFilter.CUSTOM_DATE
    }

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun clearAllFilters() {
        searchQuery.value = ""
        fileTypeFilter.value = FileTypeFilter.ALL
        selectedFolderId.value = null
        dateRangeFilter.value = DateRangeFilter.ALL
        customDateMillis.value = null
        favoritesOnly.value = false
    }

    fun toggleGridView() { isGridView.value = !isGridView.value }

    fun toggleSelection(docId: Long) {
        selectedIds.update { current ->
            if (docId in current) current - docId else current + docId
        }
    }

    fun selectAll(docIds: List<Long>) {
        selectedIds.value = docIds.toSet()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch { repository.renameDocument(documentId, newTitle) }
    }

    fun bulkRenameDocuments(
        orderedDocuments: List<DocumentEntity>,
        prefix: String,
        startNumber: Int,
        separator: RenameSeparator,
        padding: SuffixPadding
    ) {
        val renames = BulkRenameFormatter.buildPreview(
            documents = orderedDocuments,
            prefix = prefix,
            startNumber = startNumber,
            separator = separator,
            padding = padding
        ).mapNotNull { preview ->
            preview.newTitle.takeIf { it.isNotBlank() }?.let { preview.documentId to it }
        }
        if (renames.isEmpty()) return

        viewModelScope.launch {
            repository.bulkRenameDocuments(renames)
            clearSelection()
        }
    }

    fun moveDocuments(documentIds: List<Long>, targetFolderId: String) {
        viewModelScope.launch {
            repository.moveDocumentsToFolder(documentIds, targetFolderId)
            clearSelection()
        }
    }

    fun toggleDocumentFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.setDocumentFavorite(document.id, !document.isFavorite)
        }
    }

    fun setDocumentsFavorite(documentIds: List<Long>, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setDocumentsFavorite(documentIds, isFavorite)
            clearSelection()
        }
    }

    fun deleteDocuments(documentIds: List<Long>) {
        viewModelScope.launch {
            repository.deleteDocuments(documentIds)
            clearSelection()
        }
    }

    fun combineSelectedIntoSession(onReady: () -> Unit) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty() || isPreparingMerge.value) return
        viewModelScope.launch {
            isPreparingMerge.value = true
            try {
                val pages = repository.loadCombinedDraftsForDocuments(ids)
                if (pages.isNotEmpty()) {
                    val firstFolder = uiState.value.documents
                        .firstOrNull { it.id in selectedIds.value }
                        ?.folderId ?: "personal"
                    sessionHolder.startCombinedSession(
                        title = "Combined_${System.currentTimeMillis() / 1000}",
                        folderId = firstFolder,
                        pages = pages
                    )
                    clearSelection()
                    onReady()
                }
            } finally {
                isPreparingMerge.value = false
            }
        }
    }

    fun buildShareIntent(documents: List<DocumentEntity>): Intent? {
        val paths = documents.mapNotNull { it.pdfPath ?: it.thumbnailPath }
        if (paths.isEmpty()) return null
        val allPdf = documents.all { it.pdfPath != null }
        val mime = if (allPdf) "application/pdf" else "*/*"
        val title = if (documents.size == 1) documents.first().title else "Share ${documents.size} Documents"
        return fileStorage.buildShareIntent(paths, mime, title)
    }
}
