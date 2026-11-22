package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.DocumentEntity
import com.example.domain.model.DateRangeFilter
import com.example.domain.model.FileTypeFilter
import com.example.domain.model.SortOption
import com.example.ui.components.BulkRenameDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentGridCard
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MoveToFolderSheet
import com.example.ui.components.RenameDialog
import com.example.ui.components.folderIconForKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    onScanDocument: () -> Unit,
    onOpenScanSession: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderMap = remember(state.folders) { state.folders.associateBy { it.id } }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var showBulkRenameDialog by remember { mutableStateOf(false) }
    var moveIds by remember { mutableStateOf<List<Long>?>(null) }
    var deleteIds by remember { mutableStateOf<List<Long>?>(null) }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.clearSelection()
    }

    val selectedFolder = state.selectedFolderId?.let { folderMap[it] }
    val dateLabel = remember(state.dateRangeFilter, state.customDateMillis) {
        if (state.dateRangeFilter == DateRangeFilter.CUSTOM_DATE && state.customDateMillis != null) {
            val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.format(Date(state.customDateMillis!!))
        } else {
            state.dateRangeFilter.label
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .padding(horizontal = 16.dp)
        ) {
            if (state.isSelectionMode) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                                }
                                Text(
                                    text = "${state.selectedDocumentIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.selectAll(state.documents.map { it.id })
                                },
                                modifier = Modifier.testTag("select_all_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Select All")
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectionActionChip(
                                icon = Icons.Rounded.DriveFileRenameOutline,
                                label = "Rename",
                                onClick = { showBulkRenameDialog = true },
                                modifier = Modifier.testTag("bulk_rename_button")
                            )
                            SelectionActionChip(
                                icon = Icons.Rounded.PictureAsPdf,
                                label = "Combine PDF",
                                enabled = !state.isPreparingMerge,
                                onClick = { viewModel.combineSelectedIntoSession(onOpenScanSession) },
                                modifier = Modifier.testTag("merge_selected_to_pdf_button")
                            )
                            val selectedDocs = state.documents.filter { it.id in state.selectedDocumentIds }
                            val allSelectedFavorite = selectedDocs.isNotEmpty() && selectedDocs.all { it.isFavorite }
                            SelectionActionChip(
                                icon = if (allSelectedFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
                                label = if (allSelectedFavorite) "Unstar" else "Star",
                                onClick = {
                                    viewModel.setDocumentsFavorite(
                                        documentIds = state.selectedDocumentIds.toList(),
                                        isFavorite = !allSelectedFavorite
                                    )
                                },
                                modifier = Modifier.testTag("bulk_favorite_button")
                            )
                            SelectionActionChip(
                                icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                label = "Move",
                                onClick = { moveIds = state.selectedDocumentIds.toList() }
                            )
                            SelectionActionChip(
                                icon = Icons.Rounded.Share,
                                label = "Share",
                                onClick = {
                                    viewModel.buildShareIntent(selectedDocs)?.let { context.startActivity(it) }
                                }
                            )
                            SelectionActionChip(
                                icon = Icons.Rounded.Delete,
                                label = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                onClick = { deleteIds = state.selectedDocumentIds.toList() }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text("Search name, date, or OCR text...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("library_search_input")
                    )

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("library_sort_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort documents")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (state.sortOption == option) "✓ ${option.label}" else option.label
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = viewModel::toggleGridView,
                        modifier = Modifier.testTag("toggle_grid_list_button")
                    ) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = if (state.isGridView) "Switch to list view" else "Switch to grid view"
                        )
                    }
                }
            }

            if (state.isPreparingMerge) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            // Advanced Filters Row: Select Mode, Folder Filter, Creation Date Filter, File Type Chips, Clear Filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.isSelectionMode && state.documents.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.selectAll(listOf(state.documents.first().id))
                            },
                            label = { Text("Select") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = "Select documents",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("library_enter_select_chip")
                        )
                    }
                }
                // Favorites Filter Chip
                item {
                    FilterChip(
                        selected = state.favoritesOnly,
                        onClick = viewModel::toggleFavoritesOnly,
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = "Filter by favorites",
                                tint = if (state.favoritesOnly) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("library_favorites_filter_chip")
                    )
                }
                // Folder Filter Chip with Dropdown
                item {
                    Box {
                        FilterChip(
                            selected = selectedFolder != null,
                            onClick = { showFolderMenu = true },
                            label = { Text(selectedFolder?.name ?: "All Folders") },
                            leadingIcon = {
                                Icon(
                                    imageVector = selectedFolder?.let { folderIconForKey(it.iconKey) }
                                        ?: Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = selectedFolder?.let { Color(it.colorHex) }
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Select folder filter",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("library_folder_filter_chip")
                        )
                        DropdownMenu(
                            expanded = showFolderMenu,
                            onDismissRequest = { showFolderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (state.selectedFolderId == null) "✓ All Folders" else "All Folders") },
                                onClick = {
                                    viewModel.setFolderFilter(null)
                                    showFolderMenu = false
                                }
                            )
                            state.folders.forEach { folder ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = folderIconForKey(folder.iconKey),
                                            contentDescription = null,
                                            tint = Color(folder.colorHex),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            if (state.selectedFolderId == folder.id) "✓ ${folder.name}" else folder.name
                                        )
                                    },
                                    onClick = {
                                        viewModel.setFolderFilter(folder.id)
                                        showFolderMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Creation Date Filter Chip with Dropdown + DatePicker
                item {
                    Box {
                        FilterChip(
                            selected = state.dateRangeFilter != DateRangeFilter.ALL,
                            onClick = { showDateMenu = true },
                            label = { Text(dateLabel) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Select creation date filter",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("library_date_filter_chip")
                        )
                        DropdownMenu(
                            expanded = showDateMenu,
                            onDismissRequest = { showDateMenu = false }
                        ) {
                            DateRangeFilter.entries.forEach { range ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.dateRangeFilter == range) "✓ ${range.label}" else range.label
                                        )
                                    },
                                    onClick = {
                                        showDateMenu = false
                                        if (range == DateRangeFilter.CUSTOM_DATE) {
                                            showDatePickerDialog = true
                                        } else {
                                            viewModel.setDateRangeFilter(range)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                items(FileTypeFilter.entries) { filter ->
                    FilterChip(
                        selected = state.fileTypeFilter == filter,
                        onClick = { viewModel.setFileTypeFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }

                if (state.hasActiveFilters) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = viewModel::clearAllFilters,
                            label = { Text("Reset") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Reset all filters",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("library_reset_filters_chip")
                        )
                    }
                }
            }

            if (state.documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = if (state.hasActiveFilters) "No Matching Documents" else "No Documents Yet",
                        subtitle = if (state.hasActiveFilters) {
                            "Try clearing or adjusting your search query, date filter, or folder selection."
                        } else {
                            "Scan pages or import photos to start building your offline PDF library."
                        },
                        actionLabel = if (state.hasActiveFilters) "Reset Filters" else "Scan Document",
                        onAction = if (state.hasActiveFilters) viewModel::clearAllFilters else onScanDocument
                    )
                }
            } else if (state.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        val selected = doc.id in state.selectedDocumentIds
                        DocumentGridCard(
                            document = doc,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = selected,
                            onClick = {
                                if (state.isSelectionMode) viewModel.toggleSelection(doc.id)
                                else onOpenDocument(doc.id)
                            },
                            onLongClick = { viewModel.toggleSelection(doc.id) },
                            onToggleFavorite = { viewModel.toggleDocumentFavorite(doc) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        val selected = doc.id in state.selectedDocumentIds
                        DocumentListCard(
                            document = doc,
                            folder = folderMap[doc.folderId],
                            isSelectionMode = state.isSelectionMode,
                            isSelected = selected,
                            onClick = {
                                if (state.isSelectionMode) viewModel.toggleSelection(doc.id)
                                else onOpenDocument(doc.id)
                            },
                            onLongClick = { viewModel.toggleSelection(doc.id) },
                            onRename = { renameTarget = doc },
                            onMove = { moveIds = listOf(doc.id) },
                            onShare = {
                                viewModel.buildShareIntent(listOf(doc))?.let { context.startActivity(it) }
                            },
                            onDelete = { deleteIds = listOf(doc.id) },
                            onToggleFavorite = { viewModel.toggleDocumentFavorite(doc) },
                            onSelect = { viewModel.toggleSelection(doc.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.customDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { viewModel.setCustomDate(it) }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Filter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    renameTarget?.let { doc ->
        RenameDialog(
            title = "Rename Document",
            initialValue = doc.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                viewModel.renameDocument(doc.id, title)
                renameTarget = null
            }
        )
    }

    if (showBulkRenameDialog) {
        val orderedSelected = remember(state.documents, state.selectedDocumentIds) {
            state.documents.filter { it.id in state.selectedDocumentIds }
        }
        if (orderedSelected.isNotEmpty()) {
            BulkRenameDialog(
                selectedDocuments = orderedSelected,
                onDismiss = { showBulkRenameDialog = false },
                onConfirm = { prefix, startNumber, separator, padding ->
                    viewModel.bulkRenameDocuments(
                        orderedDocuments = orderedSelected,
                        prefix = prefix,
                        startNumber = startNumber,
                        separator = separator,
                        padding = padding
                    )
                    showBulkRenameDialog = false
                }
            )
        }
    }

    moveIds?.let { ids ->
        MoveToFolderSheet(
            folders = state.folders,
            onDismiss = { moveIds = null },
            onSelectFolder = { folder ->
                viewModel.moveDocuments(ids, folder.id)
                moveIds = null
            }
        )
    }

    deleteIds?.let { ids ->
        DeleteConfirmDialog(
            title = if (ids.size == 1) "Delete Document" else "Delete ${ids.size} Documents",
            message = "Are you sure you want to permanently remove the selected document(s) from device storage?",
            onDismiss = { deleteIds = null },
            onConfirm = {
                viewModel.deleteDocuments(ids)
                deleteIds = null
            }
        )
    }
}

@Composable
private fun SelectionActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val contentColor = if (enabled) tint else tint.copy(alpha = 0.45f)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.72f else 0.36f),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}
