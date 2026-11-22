package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.domain.model.BulkRenameFormatter
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfQuality
import com.example.domain.model.RenameSeparator
import com.example.domain.model.SuffixPadding
import com.example.ui.theme.FolderColorPalette
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateOrEditFolderDialog(
    initialName: String = "",
    initialColorHex: Long = FolderColorPalette.first(),
    initialIconKey: String = "folder",
    title: String = "New Folder",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: Long, iconKey: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableLongStateOf(initialColorHex) }
    var selectedIcon by remember { mutableStateOf(initialIconKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FolderColorPalette.forEach { hex ->
                            val isSelected = selectedColor == hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected color",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Icon", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvailableFolderIcons.forEach { option ->
                            val isSelected = selectedIcon == option.key
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    Color(selectedColor).copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.5.dp, Color(selectedColor))
                                } else null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedIcon = option.key }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (isSelected) Color(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedColor, selectedIcon) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_folder_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameDialog(
    title: String,
    initialValue: String,
    label: String = "Name",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag("confirm_rename_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkRenameDialog(
    selectedDocuments: List<DocumentEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        prefix: String,
        startNumber: Int,
        separator: RenameSeparator,
        padding: SuffixPadding
    ) -> Unit
) {
    var prefix by remember { mutableStateOf("") }
    var startNumberText by remember { mutableStateOf("1") }
    var separator by remember { mutableStateOf(RenameSeparator.UNDERSCORE) }
    var padding by remember { mutableStateOf(SuffixPadding.NONE) }

    val startNumber = startNumberText.toIntOrNull()?.coerceAtLeast(0) ?: 1
    val previews = remember(selectedDocuments, prefix, startNumber, separator, padding) {
        BulkRenameFormatter.buildPreview(
            documents = selectedDocuments,
            prefix = prefix,
            startNumber = startNumber,
            separator = separator,
            padding = padding
        )
    }
    val canSubmit = prefix.trim().isNotEmpty() && selectedDocuments.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Bulk Rename (${selectedDocuments.size})",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Rename selected documents with a common prefix and incremental numerical suffix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Common prefix") },
                    placeholder = { Text("e.g. Invoice") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_rename_prefix_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Start number", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val next = (startNumber - 1).coerceAtLeast(0)
                                startNumberText = next.toString()
                            },
                            enabled = startNumber > 0
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease start number")
                        }
                        OutlinedTextField(
                            value = startNumberText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(5)
                                startNumberText = digits
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bulk_rename_start_input")
                        )
                        IconButton(
                            onClick = {
                                val next = (startNumber + 1).coerceAtMost(99999)
                                startNumberText = next.toString()
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase start number")
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Separator", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RenameSeparator.entries.forEach { option ->
                            FilterChip(
                                selected = separator == option,
                                onClick = { separator = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Number format", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuffixPadding.entries.forEach { option ->
                            FilterChip(
                                selected = padding == option,
                                onClick = { padding = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (prefix.trim().isEmpty()) {
                            Text(
                                text = "Enter a prefix above to preview renamed filenames.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            previews.take(4).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.currentTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = item.newTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (previews.size > 4) {
                                Text(
                                    text = "+ ${previews.size - 4} more...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(prefix.trim(), startNumber, separator, padding) },
                enabled = canSubmit,
                modifier = Modifier.testTag("confirm_bulk_rename_button")
            ) {
                Text("Rename ${selectedDocuments.size}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(
    folders: List<FolderEntity>,
    currentFolderId: String? = null,
    onDismiss: () -> Unit,
    onSelectFolder: (FolderEntity) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Move to Folder",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            folders.forEach { folder ->
                val selected = folder.id == currentFolderId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectFolder(folder) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .minimumInteractiveComponentSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(folder.colorHex).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = folderIconForKey(folder.iconKey),
                                contentDescription = null,
                                tint = Color(folder.colorHex)
                            )
                        }
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Current folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfExportSheet(
    initialTitle: String,
    initialFolderId: String,
    initialPaperSize: PaperSize,
    initialQuality: PdfQuality,
    folders: List<FolderEntity>,
    pageCount: Int,
    onDismiss: () -> Unit,
    onExportPdf: (PdfGenerationConfig) -> Unit,
    onSaveScanOnly: (documentName: String, folderId: String) -> Unit
) {
    var docName by remember { mutableStateOf(initialTitle) }
    var selectedFolderId by remember { mutableStateOf(initialFolderId) }
    var paperSize by remember { mutableStateOf(initialPaperSize) }
    var orientation by remember { mutableStateOf(PdfOrientation.PORTRAIT) }
    var quality by remember { mutableStateOf(initialQuality) }
    var includeMargins by remember { mutableStateOf(true) }
    var protectWithPassword by remember { mutableStateOf(false) }
    var pdfPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val passwordTooShort = protectWithPassword && pdfPassword.isNotEmpty() && pdfPassword.length < 4
    val passwordMismatch = protectWithPassword && confirmPassword.isNotEmpty() && pdfPassword != confirmPassword
    val isPasswordValid = !protectWithPassword || (pdfPassword.length >= 4 && pdfPassword == confirmPassword)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Save & Generate PDF", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$pageCount ${if (pageCount == 1) "page" else "pages"} • 100% Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = docName,
                onValueChange = { docName = it },
                label = { Text("Document name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_name_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Folder", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id },
                            label = { Text(folder.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = folderIconForKey(folder.iconKey),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(folder.colorHex)
                                )
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Paper Size", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperSize.entries.forEach { size ->
                        FilterChip(
                            selected = paperSize == size,
                            onClick = { paperSize = size },
                            label = { Text(size.label.substringBefore(" (")) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Orientation", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PdfOrientation.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = orientation == item,
                            onClick = { orientation = item },
                            shape = SegmentedButtonDefaults.itemShape(index, PdfOrientation.entries.size)
                        ) {
                            Text(item.label)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PDF Quality", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfQuality.entries.forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = { Text(q.label.substringBefore(" (")) }
                        )
                    }
                }
            }

            if (paperSize != PaperSize.ORIGINAL_RATIO) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clean Page Margins", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Adds subtle white margin around scanned pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = includeMargins, onCheckedChange = { includeMargins = it })
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (protectWithPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text("Password Protection", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Encrypt PDF with a password (AES-128)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = protectWithPassword,
                            onCheckedChange = {
                                protectWithPassword = it
                                if (!it) {
                                    pdfPassword = ""
                                    confirmPassword = ""
                                }
                            },
                            modifier = Modifier.testTag("pdf_password_toggle")
                        )
                    }

                    AnimatedVisibility(visible = protectWithPassword) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = pdfPassword,
                                onValueChange = { pdfPassword = it },
                                label = { Text("PDF password (min. 4 characters)") },
                                singleLine = true,
                                isError = passwordTooShort,
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
                                supportingText = if (passwordTooShort) {
                                    { Text("Enter at least 4 characters") }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_password_input")
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm password") },
                                singleLine = true,
                                isError = passwordMismatch,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                supportingText = if (passwordMismatch) {
                                    { Text("Passwords do not match") }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_confirm_password_input")
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSaveScanOnly(docName.trim().ifBlank { "Scan" }, selectedFolderId)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Scan")
                }
                Button(
                    onClick = {
                        onExportPdf(
                            PdfGenerationConfig(
                                documentName = docName.trim().ifBlank { "ScanMate_Document" },
                                folderId = selectedFolderId,
                                paperSize = paperSize,
                                orientation = orientation,
                                quality = quality,
                                includeMargins = includeMargins,
                                password = if (protectWithPassword) pdfPassword else null
                            )
                        )
                    },
                    enabled = isPasswordValid,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("generate_pdf_confirm_button")
                ) {
                    Icon(
                        imageVector = if (protectWithPassword) Icons.Rounded.Lock else Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (protectWithPassword) "Encrypt & Save PDF" else "Generate PDF")
                }
            }
        }
    }
}

@Composable
fun SetPdfPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val tooShort = password.isNotEmpty() && password.length < 4
    val mismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val canSubmit = password.length >= 4 && password == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Set PDF Password", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Encrypt this PDF offline with AES-128 security. Anyone opening it will need this password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (min. 4 characters)") },
                    singleLine = true,
                    isError = tooShort,
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
                    supportingText = if (tooShort) {
                        { Text("Enter at least 4 characters") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_pdf_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = if (mismatch) {
                        { Text("Passwords do not match") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_pdf_confirm_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = canSubmit,
                modifier = Modifier.testTag("confirm_set_pdf_password_button")
            ) {
                Text("Encrypt PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultSheet(
    selectedLanguage: OcrLanguage,
    isRunning: Boolean,
    ocrResult: OcrResult?,
    existingText: String,
    onSelectLanguage: (OcrLanguage) -> Unit,
    onRunOcr: () -> Unit,
    onSaveText: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedBanner by remember { mutableStateOf(false) }

    val displayText = when (ocrResult) {
        is OcrResult.Success -> ocrResult.text
        else -> existingText
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Column {
                        Text("Offline Text Recognition", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "On-device OCR • No internet used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = { onSelectLanguage(lang) },
                        label = {
                            Text(
                                if (lang.isBundled) "${lang.displayName} (Bundled)"
                                else "${lang.displayName} (Pack Needed)"
                            )
                        }
                    )
                }
            }

            if (!selectedLanguage.isBundled || ocrResult is OcrResult.LanguageUnavailable) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = (ocrResult as? OcrResult.LanguageUnavailable)?.message
                                ?: selectedLanguage.statusNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Button(
                onClick = onRunOcr,
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_ocr_button")
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting Text Offline...")
                } else {
                    Text(if (displayText.isBlank()) "Extract Text Now" else "Re-scan Text (${selectedLanguage.displayName})")
                }
            }

            when (ocrResult) {
                is OcrResult.NoTextFound -> {
                    Text(
                        "No readable text was detected in this image.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is OcrResult.Error -> {
                    Text(
                        ocrResult.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> Unit
            }

            if (displayText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 260.dp)
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }

                if (copiedBanner) {
                    Text(
                        text = "Copied extracted text to clipboard",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", displayText))
                            copiedBanner = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, displayText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    if (onSaveText != null) {
                        Button(
                            onClick = { onSaveText(displayText) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
