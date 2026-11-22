package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.model.ScanDraftPage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OcrResultSheet
import com.example.ui.components.PdfExportSheet
import com.example.ui.components.ReorderableDragHandle
import com.example.ui.components.ReorderableLazyListState
import com.example.ui.components.ReorderablePageItem
import com.example.ui.components.rememberReorderableLazyListState
import com.example.ui.components.reorderableContainer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSessionScreen(
    onBack: () -> Unit,
    onAddCameraPage: () -> Unit,
    onEditPage: (String) -> Unit,
    onCropPage: (String) -> Unit,
    onDocumentSaved: (Long) -> Unit,
    viewModel: ScanSessionViewModel = hiltViewModel()
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val defaultPaper by viewModel.defaultPaperSize.collectAsStateWithLifecycle()
    val defaultQuality by viewModel.defaultPdfQuality.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }
    var activeOcrPage by remember { mutableStateOf<ScanDraftPage?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMoreImages(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.documentTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = if (session.pages.size > 1) {
                                "${session.pages.size} pages • Drag handle or hold to reorder"
                            } else {
                                "${session.pages.size} ${if (session.pages.size == 1) "page" else "pages"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (exportState.isExporting) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = {
                                    if (exportState.totalPages > 0) {
                                        exportState.currentPage.toFloat() / exportState.totalPages
                                    } else 0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Generating offline PDF (${exportState.currentPage}/${exportState.totalPages})...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddCameraPage,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_add_camera_button")
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_add_gallery_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery")
                        }
                    }

                    Button(
                        onClick = { showExportSheet = true },
                        enabled = session.pages.isNotEmpty() && !exportState.isExporting,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("session_save_pdf_button")
                    ) {
                        if (exportState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Building PDF...")
                        } else {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save & Generate PDF (${session.pages.size})")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (session.pages.isEmpty()) {
                EmptyStateView(
                    title = "No Pages in Session",
                    subtitle = "Capture a page with the camera or import photos from your gallery to assemble a multi-page PDF.",
                    actionLabel = "Open Scanner",
                    onAction = onAddCameraPage,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val listState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(
                    lazyListState = listState,
                    enabled = session.pages.size > 1,
                    onMove = { fromIndex, toIndex ->
                        viewModel.movePage(fromIndex, toIndex)
                    }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                        .reorderableContainer(reorderState)
                        .testTag("session_pages_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                        ReorderablePageItem(
                            state = reorderState,
                            itemKey = page.id
                        ) { isDragging ->
                            SessionPageCard(
                                index = index,
                                totalCount = session.pages.size,
                                page = page,
                                isDragging = isDragging,
                                reorderState = reorderState,
                                onMoveUp = {
                                    if (index > 0) viewModel.movePage(index, index - 1)
                                },
                                onMoveDown = {
                                    if (index < session.pages.lastIndex) viewModel.movePage(index, index + 1)
                                },
                                onCrop = { onCropPage(page.id) },
                                onEdit = { onEditPage(page.id) },
                                onRotate = { viewModel.rotatePage(page.id) },
                                onOcr = {
                                    viewModel.clearOcrResult()
                                    activeOcrPage = page
                                },
                                onDelete = { viewModel.deletePage(page.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        PdfExportSheet(
            initialTitle = session.documentTitle,
            initialFolderId = session.targetFolderId,
            initialPaperSize = defaultPaper,
            initialQuality = defaultQuality,
            folders = folders,
            pageCount = session.pages.size,
            onDismiss = { showExportSheet = false },
            onExportPdf = { config ->
                showExportSheet = false
                viewModel.setDocumentTitle(config.documentName)
                viewModel.setTargetFolder(config.folderId)
                viewModel.exportSession(config = config, exportAsPdf = true, onSaved = onDocumentSaved)
            },
            onSaveScanOnly = { name, folderId ->
                showExportSheet = false
                val config = PdfGenerationConfig(
                    documentName = name,
                    folderId = folderId,
                    paperSize = defaultPaper,
                    quality = defaultQuality
                )
                viewModel.exportSession(config = config, exportAsPdf = false, onSaved = onDocumentSaved)
            }
        )
    }

    activeOcrPage?.let { targetPage ->
        val latestPage = session.pages.find { it.id == targetPage.id } ?: targetPage
        OcrResultSheet(
            selectedLanguage = ocrState.selectedLanguage,
            isRunning = ocrState.isRunning,
            ocrResult = ocrState.result,
            existingText = latestPage.ocrText,
            onSelectLanguage = viewModel::setOcrLanguage,
            onRunOcr = { viewModel.runOcrForPage(latestPage.id) },
            onSaveText = { text ->
                viewModel.savePageOcrText(latestPage.id, text)
                activeOcrPage = null
            },
            onDismiss = { activeOcrPage = null }
        )
    }

    exportState.errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearExportError,
            title = { Text("PDF Export Error") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = viewModel::clearExportError) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SessionPageCard(
    index: Int,
    totalCount: Int,
    page: ScanDraftPage,
    isDragging: Boolean,
    reorderState: ReorderableLazyListState,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCrop: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onOcr: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = if (isDragging) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_page_card_${index + 1}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReorderableDragHandle(
                state = reorderState,
                itemKey = page.id,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isDragging) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                alpha = if (totalCount > 1) 0.75f else 0.35f
                            )
                        }
                    )
                    .semantics {
                        contentDescription = if (totalCount > 1) {
                            "Drag to reorder page ${index + 1}"
                        } else {
                            "Page ${index + 1}"
                        }
                    }
                    .testTag("session_drag_handle_${index + 1}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = when {
                        isDragging -> MaterialTheme.colorScheme.primary
                        totalCount > 1 -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 94.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onEdit)
            ) {
                AsyncImage(
                    model = File(page.processedImagePath),
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Page ${index + 1}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${page.filter.label}${if (page.ocrText.isNotBlank()) " • OCR Ready" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("session_move_up_${index + 1}")
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move page up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < totalCount - 1,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("session_move_down_${index + 1}")
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move page down", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onCrop, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Crop, contentDescription = "Crop page", modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Filter and enhance", modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onRotate, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate page", modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onOcr, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.TextFields, contentDescription = "Extract text OCR", modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete page",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}
