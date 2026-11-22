package com.example.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.domain.model.BatterySaverProfile
import com.example.ui.components.CreateOrEditFolderDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentListCard
import com.example.ui.components.DocumentTypeBadge
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MoveToFolderSheet
import com.example.ui.components.OfflinePrivacyBadge
import com.example.ui.components.RenameDialog
import com.example.ui.components.folderIconForKey
import com.example.ui.components.formatFileSize
import java.io.File
import java.util.Calendar

@Composable
fun HomeScreen(
    onScanDocument: () -> Unit,
    onOpenScanSession: () -> Unit,
    onBrowseDocuments: () -> Unit,
    onBrowseFolders: () -> Unit = {},
    onOpenDocument: (Long) -> Unit,
    onOpenFolder: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshStorage() }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var moveTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentEntity?>(null) }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesIntoNewSession(uris, onReady = onOpenScanSession)
        }
    }

    val pdfOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val title = "Imported_${System.currentTimeMillis() / 1000}"
            viewModel.importExternalPdf(uri, title) { docId ->
                onOpenDocument(docId)
            }
        }
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val folderMap = remember(state.folders) { state.folders.associateBy { it.id } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                // Hero Greeting & Stats Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = greeting,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "ScanMate Vault",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OfflinePrivacyBadge()
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatPill(
                                    label = "Documents",
                                    value = state.totalDocuments.toString(),
                                    icon = Icons.Rounded.Description,
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = "PDF Files",
                                    value = state.totalPdfFiles.toString(),
                                    icon = Icons.Rounded.PictureAsPdf,
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = "Storage",
                                    value = formatFileSize(state.totalStorageBytes),
                                    icon = Icons.Rounded.Storage,
                                    modifier = Modifier.weight(1.15f)
                                )
                            }
                        }
                    }
                }
            }

            if (state.batterySaver.isActive) {
                item {
                    HomeBatterySaverBanner(
                        profile = state.batterySaver,
                        onOpenSettings = onOpenSettings
                    )
                }
            }

            if (state.isImporting) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Processing imported pages offline...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleLarge
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            QuickActionTile(
                                title = "Scan Doc",
                                subtitle = "Camera",
                                icon = Icons.Rounded.DocumentScanner,
                                tint = MaterialTheme.colorScheme.primary,
                                testTag = "quick_scan_button",
                                onClick = {
                                    viewModel.startNewSession()
                                    onScanDocument()
                                }
                            )
                        }
                        item {
                            QuickActionTile(
                                title = "Import Image",
                                subtitle = "Gallery",
                                icon = Icons.Rounded.AddPhotoAlternate,
                                tint = MaterialTheme.colorScheme.secondary,
                                testTag = "quick_import_image_button",
                                onClick = {
                                    multiPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                        item {
                            QuickActionTile(
                                title = "Create PDF",
                                subtitle = "Multi-page",
                                icon = Icons.Rounded.PictureAsPdf,
                                tint = MaterialTheme.colorScheme.tertiary,
                                testTag = "quick_create_pdf_button",
                                onClick = {
                                    viewModel.startNewSession()
                                    onOpenScanSession()
                                }
                            )
                        }
                        item {
                            QuickActionTile(
                                title = "Browse Docs",
                                subtitle = "Library",
                                icon = Icons.Rounded.FolderOpen,
                                tint = Color(0xFF7C3AED),
                                testTag = "quick_browse_button",
                                onClick = onBrowseDocuments
                            )
                        }
                        item {
                            QuickActionTile(
                                title = "New Folder",
                                subtitle = "Organize",
                                icon = Icons.Rounded.CreateNewFolder,
                                tint = Color(0xFFD97706),
                                testTag = "quick_new_folder_button",
                                onClick = { showCreateFolderDialog = true }
                            )
                        }
                        item {
                            QuickActionTile(
                                title = "Open PDF",
                                subtitle = "Device",
                                icon = Icons.Rounded.UploadFile,
                                tint = Color(0xFF475569),
                                testTag = "quick_open_pdf_button",
                                onClick = { pdfOpenLauncher.launch(arrayOf("application/pdf")) }
                            )
                        }
                    }
                }
            }

            item {
                HomeFavoritesSection(
                    favoriteFolders = state.favoriteFolders,
                    favoriteDocuments = state.favoriteDocuments,
                    folderDocumentCounts = state.folderDocumentCounts,
                    folderMap = folderMap,
                    hasAnyDocuments = state.totalDocuments > 0,
                    onOpenFolder = onOpenFolder,
                    onOpenDocument = onOpenDocument,
                    onToggleFolderFavorite = viewModel::toggleFolderFavorite,
                    onToggleDocumentFavorite = viewModel::toggleDocumentFavorite,
                    onBrowseDocuments = onBrowseDocuments,
                    onBrowseFolders = onBrowseFolders
                )
            }

            if (state.recentPages.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Recently Scanned Pages",
                            style = MaterialTheme.typography.titleLarge
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.recentPages, key = { it.id }) { page ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 2.dp,
                                    modifier = Modifier
                                        .size(width = 88.dp, height = 114.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onOpenDocument(page.documentId) }
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = File(page.processedImagePath),
                                            contentDescription = "Page ${page.pageIndex + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "P${page.pageIndex + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (state.recentDocuments.isNotEmpty()) {
                        TextButton(onClick = onBrowseDocuments) {
                            Text("See All")
                        }
                    }
                }
            }

            if (state.recentDocuments.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "Your ScanMate Vault is Ready",
                        subtitle = "Scan a physical document with edge detection or import photos from your gallery to build crisp offline PDFs.",
                        actionLabel = "Scan First Document",
                        onAction = {
                            viewModel.startNewSession()
                            onScanDocument()
                        }
                    )
                }
            } else {
                items(state.recentDocuments, key = { it.id }) { doc ->
                    DocumentListCard(
                        document = doc,
                        folder = folderMap[doc.folderId],
                        isSelectionMode = false,
                        isSelected = false,
                        onClick = { onOpenDocument(doc.id) },
                        onLongClick = { moveTarget = doc },
                        onRename = { renameTarget = doc },
                        onMove = { moveTarget = doc },
                        onShare = {
                            viewModel.buildShareIntent(doc)?.let { context.startActivity(it) }
                        },
                        onDelete = { deleteTarget = doc },
                        onToggleFavorite = { viewModel.toggleDocumentFavorite(doc) }
                    )
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateOrEditFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color, icon ->
                viewModel.createFolder(name, color, icon)
                showCreateFolderDialog = false
            }
        )
    }

    renameTarget?.let { doc ->
        RenameDialog(
            title = "Rename Document",
            initialValue = doc.title,
            label = "Document title",
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                viewModel.renameDocument(doc.id, newTitle)
                renameTarget = null
            }
        )
    }

    moveTarget?.let { doc ->
        MoveToFolderSheet(
            folders = state.folders,
            currentFolderId = doc.folderId,
            onDismiss = { moveTarget = null },
            onSelectFolder = { folder ->
                viewModel.moveDocument(doc.id, folder.id)
                moveTarget = null
            }
        )
    }

    deleteTarget?.let { doc ->
        DeleteConfirmDialog(
            title = "Delete Document",
            message = "Delete \"${doc.title}\" and all its local scanned pages? This cannot be undone.",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteDocument(doc.id)
                deleteTarget = null
            }
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .width(116.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HomeFavoritesSection(
    favoriteFolders: List<FolderEntity>,
    favoriteDocuments: List<DocumentEntity>,
    folderDocumentCounts: Map<String, Int>,
    folderMap: Map<String, FolderEntity>,
    hasAnyDocuments: Boolean,
    onOpenFolder: (String) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onToggleFolderFavorite: (FolderEntity) -> Unit,
    onToggleDocumentFavorite: (DocumentEntity) -> Unit,
    onBrowseDocuments: () -> Unit,
    onBrowseFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFavorites = favoriteFolders.size + favoriteDocuments.size
    val starAmber = Color(0xFFF59E0B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_favorites_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = starAmber.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = starAmber,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleLarge
                )
                if (totalFavorites > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = totalFavorites.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (totalFavorites == 0 && hasAnyDocuments) {
                TextButton(onClick = onBrowseDocuments) {
                    Text("Browse Docs")
                }
            } else if (favoriteFolders.isNotEmpty()) {
                TextButton(onClick = onBrowseFolders) {
                    Text("Folders")
                }
            }
        }

        if (totalFavorites == 0) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_favorites_empty")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(starAmber.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StarOutline,
                            contentDescription = null,
                            tint = starAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Pin Important Docs & Folders",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tap the star icon on any document or folder to keep quick offline shortcuts here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            if (favoriteFolders.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("home_favorite_folders_row")
                ) {
                    items(favoriteFolders, key = { "fav_folder_${it.id}" }) { folder ->
                        val docCount = folderDocumentCounts[folder.id] ?: 0
                        FavoriteFolderCard(
                            folder = folder,
                            documentCount = docCount,
                            onClick = { onOpenFolder(folder.id) },
                            onToggleFavorite = { onToggleFolderFavorite(folder) }
                        )
                    }
                }
            }

            if (favoriteDocuments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("home_favorite_docs_row")
                ) {
                    items(favoriteDocuments, key = { "fav_doc_${it.id}" }) { doc ->
                        FavoriteDocumentCard(
                            document = doc,
                            folder = folderMap[doc.folderId],
                            onClick = { onOpenDocument(doc.id) },
                            onToggleFavorite = { onToggleDocumentFavorite(doc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteFolderCard(
    folder: FolderEntity,
    documentCount: Int,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val accent = Color(folder.colorHex)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .width(172.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("favorite_folder_card_${folder.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folderIconForKey(folder.iconKey),
                        contentDescription = folder.name,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("home_favorite_folder_star_${folder.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Remove ${folder.name} from favorites",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$documentCount ${if (documentCount == 1) "file" else "files"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FavoriteDocumentCard(
    document: DocumentEntity,
    folder: FolderEntity?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("favorite_doc_card_${document.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = document.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (document.fileType == "PDF") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocumentTypeBadge(fileType = document.fileType)
                    if (document.isPasswordProtected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Password protected",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(11.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("home_favorite_doc_star_${document.id}")
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.52f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Remove ${document.title} from favorites",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier
                                .padding(5.dp)
                                .size(16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val folderLabel = folder?.name ?: document.fileType
                Text(
                    text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} • $folderLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HomeBatterySaverBanner(
    profile: BatterySaverProfile,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_battery_saver_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatterySaver,
                    contentDescription = "Battery saver active",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Battery Saver Active • ${profile.batteryPercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Scan resolution capped at ${profile.maxScanDimensionPx}px with ${(profile.enhancementIntensity * 100).toInt()}% filter intensity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("home_battery_saver_settings_button")
            ) {
                Text("Settings")
            }
        }
    }
}
