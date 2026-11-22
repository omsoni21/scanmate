package com.example.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.FolderEntity
import com.example.ui.components.CreateOrEditFolderDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.RenameDialog
import com.example.ui.components.folderIconForKey
import com.example.ui.components.formatFileSize

@Composable
fun FoldersScreen(
    onOpenFolder: (String) -> Unit,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var renameFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search folders...") },
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
                        .testTag("folder_search_input")
                )

                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("create_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.folders, key = { it.folder.id }) { item ->
                    FolderGridCard(
                        item = item,
                        onClick = { onOpenFolder(item.folder.id) },
                        onToggleFavorite = { viewModel.toggleFolderFavorite(item.folder) },
                        onEditAppearance = { editFolderTarget = item.folder },
                        onRename = { renameFolderTarget = item.folder },
                        onDelete = { deleteFolderTarget = item.folder }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditFolderDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, icon ->
                viewModel.createFolder(name, color, icon)
                showCreateDialog = false
            }
        )
    }

    editFolderTarget?.let { folder ->
        CreateOrEditFolderDialog(
            initialName = folder.name,
            initialColorHex = folder.colorHex,
            initialIconKey = folder.iconKey,
            title = "Customize Folder",
            confirmLabel = "Save",
            onDismiss = { editFolderTarget = null },
            onConfirm = { name, color, icon ->
                viewModel.updateFolderAppearance(folder.copy(name = name), color, icon)
                editFolderTarget = null
            }
        )
    }

    renameFolderTarget?.let { folder ->
        RenameDialog(
            title = "Rename Folder",
            initialValue = folder.name,
            label = "Folder name",
            onDismiss = { renameFolderTarget = null },
            onConfirm = { newName ->
                viewModel.renameFolder(folder, newName)
                renameFolderTarget = null
            }
        )
    }

    deleteFolderTarget?.let { folder ->
        DeleteConfirmDialog(
            title = "Delete Custom Folder",
            message = "Delete \"${folder.name}\"? Any documents inside will be moved safely to \"Other\".",
            onDismiss = { deleteFolderTarget = null },
            onConfirm = {
                viewModel.deleteCustomFolder(folder.id)
                deleteFolderTarget = null
            }
        )
    }
}

@Composable
private fun FolderGridCard(
    item: FolderWithStats,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditAppearance: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val folder = item.folder
    val accent = Color(folder.colorHex)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("folder_card_${folder.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folderIconForKey(folder.iconKey),
                        contentDescription = folder.name,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("favorite_folder_button_${folder.id}")
                    ) {
                        Icon(
                            imageVector = if (folder.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (folder.isFavorite) {
                                "Remove ${folder.name} from favorites"
                            } else {
                                "Add ${folder.name} to favorites"
                            },
                            tint = if (folder.isFavorite) {
                                Color(0xFFF59E0B)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Folder options",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (folder.isFavorite) "Remove from Favorites" else "Add to Favorites")
                                },
                                onClick = { menuExpanded = false; onToggleFavorite() }
                            )
                            DropdownMenuItem(
                                text = { Text("Customize Color & Icon") },
                                onClick = { menuExpanded = false; onEditAppearance() }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            if (!folder.isPredefined) {
                                DropdownMenuItem(
                                    text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuExpanded = false; onDelete() }
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.documentCount} ${if (item.documentCount == 1) "file" else "files"} • ${formatFileSize(item.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
