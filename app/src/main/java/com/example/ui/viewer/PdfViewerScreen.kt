package com.example.ui.viewer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.OcrResultSheet
import com.example.ui.components.RenameDialog
import com.example.ui.components.SetPdfPasswordDialog
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: Long,
    onBack: () -> Unit,
    onEditPages: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val stateFlow = remember(documentId) { viewModel.observeDocumentState(documentId) }
    val state by stateFlow.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var currentPage by remember { mutableIntStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isFullScreen by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }

    val doc = state.document
    val total = state.pageCount.coerceAtLeast(1)

    LaunchedEffect(doc, currentPage) {
        if (doc != null) {
            viewModel.ensurePageRendered(doc, currentPage)
        }
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && doc != null) {
            viewModel.exportPdfToUri(doc, uri) { success ->
                Toast.makeText(
                    context,
                    if (success) "PDF exported successfully" else "Export failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // Main Zoomable Page Canvas
        val pageBitmap = state.renderedBitmaps[currentPage]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1.1f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                zoomScale = 2.2f
                            }
                        },
                        onTap = { isFullScreen = !isFullScreen }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = nextScale
                        panOffset = if (nextScale <= 1.02f) {
                            Offset.Zero
                        } else {
                            Offset(
                                x = (panOffset.x + pan.x).coerceIn(-1200f, 1200f),
                                y = (panOffset.y + pan.y).coerceIn(-1800f, 1800f)
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(color = Color.White)
                state.requiresPassword && doc != null -> {
                    var passwordInput by remember { mutableStateOf("") }
                    var passwordVisible by remember { mutableStateOf(false) }

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Password Protected PDF",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Enter the password to unlock and view \"${doc.title}\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("PDF password") },
                                singleLine = true,
                                isError = state.passwordError != null,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                supportingText = state.passwordError?.let { err -> { Text(err) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unlock_pdf_password_input")
                            )
                            Button(
                                onClick = { viewModel.unlockWithPassword(doc, passwordInput) },
                                enabled = passwordInput.isNotEmpty() && !state.isUnlocking,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unlock_pdf_button")
                            ) {
                                if (state.isUnlocking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Unlock PDF")
                                }
                            }
                        }
                    }
                }
                pageBitmap != null -> {
                    Image(
                        bitmap = pageBitmap.asImageBitmap(),
                        contentDescription = "Page ${currentPage + 1} of $total",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 72.dp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                    )
                }
                else -> {
                    Text(
                        text = "Unable to render page preview.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Top Bar Chrome
        AnimatedVisibility(
            visible = !isFullScreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xCC0B1120),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Column {
                        Text(
                            text = doc?.title ?: "Document",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (doc != null) {
                            Text(
                                text = "${doc.fileType} • ${formatFileSize(doc.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
                    }
                },
                actions = {
                    if (doc != null) {
                        IconButton(
                            onClick = { viewModel.toggleFavorite(doc) },
                            modifier = Modifier.testTag("viewer_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (doc.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = if (doc.isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (doc.isFavorite) Color(0xFFFBBF24) else Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = { showOcrSheet = true },
                        modifier = Modifier.testTag("viewer_ocr_button")
                    ) {
                        Icon(Icons.Rounded.TextFields, contentDescription = "Extract text OCR")
                    }
                    IconButton(
                        onClick = {
                            if (doc != null) {
                                viewModel.buildShareIntent(doc)?.let { context.startActivity(it) }
                            }
                        },
                        modifier = Modifier.testTag("viewer_share_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share document")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (doc?.pdfPath != null) {
                                DropdownMenuItem(
                                    text = { Text("Export Copy to Device") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        exportSafLauncher.launch("${doc.title}.pdf")
                                    }
                                )
                                if (!doc.isPasswordProtected) {
                                    DropdownMenuItem(
                                        text = { Text("Set PDF Password") },
                                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showSetPasswordDialog = true
                                        }
                                    )
                                }
                            }
                            if (state.pages.isNotEmpty() && doc != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit Pages") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.editPagesInSession(doc, onEditPages)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                onClick = { showMenu = false; showRenameDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Document Info") },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                onClick = { showMenu = false; showInfoDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { showMenu = false; showDeleteDialog = true }
                            )
                        }
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }

        // Bottom Page Navigation & Zoom Bar
        AnimatedVisibility(
            visible = !isFullScreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xD90B1120),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (currentPage > 0) {
                                currentPage--
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                        },
                        enabled = currentPage > 0
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.NavigateBefore,
                            contentDescription = "Previous page",
                            tint = if (currentPage > 0) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        text = "${currentPage + 1} / $total",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    IconButton(
                        onClick = {
                            if (currentPage < total - 1) {
                                currentPage++
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                        },
                        enabled = currentPage < total - 1
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.NavigateNext,
                            contentDescription = "Next page",
                            tint = if (currentPage < total - 1) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }

                    Spacer(Modifier.size(8.dp))

                    IconButton(onClick = { zoomScale = (zoomScale - 0.5f).coerceAtLeast(1f) }) {
                        Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out", tint = Color.White)
                    }
                    IconButton(onClick = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }) {
                        Icon(Icons.Rounded.FitScreen, contentDescription = "Fit to screen", tint = Color.White)
                    }
                    IconButton(onClick = { zoomScale = (zoomScale + 0.5f).coerceAtMost(4f) }) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in", tint = Color.White)
                    }
                    IconButton(onClick = { isFullScreen = true }) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen", tint = Color.White)
                    }
                }
            }
        }

        if (isFullScreen) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                IconButton(onClick = { isFullScreen = false }) {
                    Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit full screen", tint = Color.White)
                }
            }
        }
    }

    if (showRenameDialog && doc != null) {
        RenameDialog(
            title = "Rename Document",
            initialValue = doc.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { title ->
                viewModel.renameDocument(doc.id, title)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog && doc != null) {
        DeleteConfirmDialog(
            title = "Delete Document",
            message = "Delete \"${doc.title}\" permanently from device storage?",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteDocument(doc.id, onDeleted = onBack)
            }
        )
    }

    if (showInfoDialog && doc != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Document Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", doc.title)
                    InfoRow("Format", doc.fileType)
                    InfoRow("Security", if (doc.isPasswordProtected) "Password Protected (AES-128)" else "Standard Unencrypted")
                    InfoRow("Pages", "${state.pageCount.coerceAtLeast(doc.pageCount)}")
                    InfoRow("File Size", formatFileSize(doc.fileSizeBytes))
                    InfoRow("Folder", state.folder?.name ?: "Other")
                    InfoRow("Created", formatTimestamp(doc.createdAt))
                    InfoRow("Updated", formatTimestamp(doc.updatedAt))
                    InfoRow("OCR Text", if (doc.ocrText.isBlank()) "Not extracted yet" else "${doc.ocrText.length} characters saved")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }

    if (showOcrSheet && doc != null) {
        OcrResultSheet(
            selectedLanguage = ocrState.selectedLanguage,
            isRunning = ocrState.isRunning,
            ocrResult = ocrState.result,
            existingText = doc.ocrText,
            onSelectLanguage = viewModel::setOcrLanguage,
            onRunOcr = { viewModel.runOcr(doc.id) },
            onSaveText = { text ->
                viewModel.saveOcrText(doc.id, text)
                showOcrSheet = false
            },
            onDismiss = { showOcrSheet = false }
        )
    }

    if (showSetPasswordDialog && doc != null) {
        SetPdfPasswordDialog(
            onDismiss = { showSetPasswordDialog = false },
            onConfirm = { password ->
                showSetPasswordDialog = false
                viewModel.protectWithPassword(doc.id, password) { success ->
                    Toast.makeText(
                        context,
                        if (success) "PDF encrypted with password" else "Failed to encrypt PDF",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
