package com.example.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.DocumentEntity
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MoveToFolderSheet
import com.example.ui.components.RenameDialog
import com.example.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: String,
    onBack: () -> Unit,
    onScanIntoFolder: (String) -> Unit,
    onOpenDocument: (Long) -> Unit,
    foldersViewModel: FoldersViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val folder by foldersViewModel.observeFolder(folderId).collectAsStateWithLifecycle(initialValue = null)
    val docs by foldersViewModel.observeDocumentsInFolder(folderId).collectAsStateWithLifecycle(initialValue = emptyList())
    val foldersState by foldersViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var moveTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "Folder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
                    }
                },
                actions = {
                    folder?.let { currentFolder ->
                        IconButton(
                            onClick = { foldersViewModel.toggleFolderFavorite(currentFolder) },
                            modifier = Modifier.testTag("folder_detail_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (currentFolder.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = if (currentFolder.isFavorite) {
                                    "Remove folder from favorites"
                                } else {
                                    "Add folder to favorites"
                                },
                                tint = if (currentFolder.isFavorite) {
                                    Color(0xFFF59E0B)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onScanIntoFolder(folderId) },
                icon = { Icon(Icons.Rounded.DocumentScanner, contentDescription = null) },
                text = { Text("Scan Here") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (docs.isEmpty()) {
                EmptyStateView(
                    title = "Empty Folder",
                    subtitle = "No documents in ${folder?.name ?: "this folder"} yet.",
                    actionLabel = "Scan into Folder",
                    onAction = { onScanIntoFolder(folderId) },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(docs, key = { it.id }) { doc ->
                        DocumentListCard(
                            document = doc,
                            folder = folder,
                            isSelectionMode = false,
                            isSelected = false,
                            onClick = { onOpenDocument(doc.id) },
                            onLongClick = { moveTarget = doc },
                            onRename = { renameTarget = doc },
                            onMove = { moveTarget = doc },
                            onShare = {
                                homeViewModel.buildShareIntent(doc)?.let { context.startActivity(it) }
                            },
                            onDelete = { deleteTarget = doc },
                            onToggleFavorite = { foldersViewModel.toggleDocumentFavorite(doc) }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { doc ->
        RenameDialog(
            title = "Rename Document",
            initialValue = doc.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                foldersViewModel.renameDocument(doc.id, newTitle)
                renameTarget = null
            }
        )
    }

    moveTarget?.let { doc ->
        MoveToFolderSheet(
            folders = foldersState.folders.map { it.folder },
            currentFolderId = doc.folderId,
            onDismiss = { moveTarget = null },
            onSelectFolder = { target ->
                foldersViewModel.moveDocument(doc.id, target.id)
                moveTarget = null
            }
        )
    }

    deleteTarget?.let { doc ->
        DeleteConfirmDialog(
            title = "Delete Document",
            message = "Delete \"${doc.title}\" from device storage?",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                foldersViewModel.deleteDocument(doc.id)
                deleteTarget = null
            }
        )
    }
}
