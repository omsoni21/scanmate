package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.processing.ImageProcessor
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.DocumentFilter
import com.example.domain.model.ImageQuality
import com.example.domain.model.QuadPoints
import com.example.ui.components.OcrResultSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    fileStorage: LocalFileStorage,
    onBack: () -> Unit,
    onOpenCrop: (String) -> Unit,
    onDoneToSession: () -> Unit,
    viewModel: ScanSessionViewModel = hiltViewModel()
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val batterySaver by viewModel.batterySaverProfile.collectAsStateWithLifecycle()
    val page = session.pages.find { it.id == pageId }

    var selectedFilter by remember(pageId) { mutableStateOf(page?.filter ?: DocumentFilter.AUTO_ENHANCE) }
    var brightness by remember(pageId) { mutableFloatStateOf(page?.brightness ?: 0f) }
    var contrast by remember(pageId) { mutableFloatStateOf(page?.contrast ?: 1f) }
    var sharpness by remember(pageId) { mutableFloatStateOf(page?.sharpness ?: batterySaver.defaultSharpness) }
    var rotationDegrees by remember(pageId) { mutableIntStateOf(page?.rotationDegrees ?: 0) }
    var quality by remember(pageId) { mutableStateOf(page?.imageQuality ?: batterySaver.scanQuality) }
    var overwriteOriginal by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    var baseWarpedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isComparingOriginal by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }

    // Load cropped/warped base preview for fast interactive filtering
    LaunchedEffect(page?.originalImagePath, page?.cropCorners, rotationDegrees, batterySaver.maxPreviewDimensionPx) {
        val current = page ?: return@LaunchedEffect
        val previewMaxDim = if (batterySaver.isActive) {
            minOf(ImageQuality.COMPACT.maxDimensionPx, batterySaver.maxPreviewDimensionPx)
        } else {
            ImageQuality.COMPACT.maxDimensionPx
        }
        val loaded = withContext(Dispatchers.IO) {
            val orig = fileStorage.loadBitmap(current.originalImagePath, previewMaxDim) ?: return@withContext null
            val warped = ImageProcessor.warpPerspectiveAndRotate(
                source = orig,
                quad = current.cropCorners,
                rotationDegrees = rotationDegrees,
                quality = ImageQuality.COMPACT,
                maxDimensionOverridePx = previewMaxDim
            )
            orig.recycle()
            warped
        }
        baseWarpedBitmap = loaded
    }

    // Recompute live preview when filter, sliders, or battery saver intensity change
    LaunchedEffect(baseWarpedBitmap, selectedFilter, brightness, contrast, sharpness, batterySaver.enhancementIntensity) {
        val base = baseWarpedBitmap ?: return@LaunchedEffect
        delay(60)
        val enhanced = withContext(Dispatchers.Default) {
            ImageProcessor.applyEnhancement(
                source = base,
                filter = selectedFilter,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                enhancementIntensity = batterySaver.enhancementIntensity
            )
        }
        previewBitmap = enhanced
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enhance & Filter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onOpenCrop(pageId) },
                        modifier = Modifier.testTag("editor_crop_button")
                    ) {
                        Icon(Icons.Rounded.Crop, contentDescription = "Crop and perspective")
                    }
                    IconButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        modifier = Modifier.testTag("editor_rotate_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees")
                    }
                    IconButton(
                        onClick = {
                            viewModel.clearOcrResult()
                            showOcrSheet = true
                        },
                        modifier = Modifier.testTag("editor_ocr_button")
                    ) {
                        Icon(Icons.Rounded.TextFields, contentDescription = "Extract text OCR")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Live Preview Viewport with Before/After hold or toggle
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isComparingOriginal = true
                                tryAwaitRelease()
                                isComparingOriginal = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val shownBitmap = if (isComparingOriginal) baseWarpedBitmap else previewBitmap
                if (shownBitmap != null) {
                    Image(
                        bitmap = shownBitmap.asImageBitmap(),
                        contentDescription = "Enhanced page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }

                // Battery Saver indicator pill when active
                if (batterySaver.isActive) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .testTag("editor_battery_saver_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatterySaver,
                                contentDescription = "Battery saver active",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Battery Saver • ${(batterySaver.enhancementIntensity * 100).toInt()}% enhance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Compare Before/After pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { isComparingOriginal = !isComparingOriginal }
                        .testTag("compare_before_after_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Compare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isComparingOriginal) "Before (Original)" else "Hold / Tap to Compare",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // Editing Controls Sheet
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 7 Filters Carousel
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(DocumentFilter.entries) { filter ->
                            val selected = selectedFilter == filter
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedFilter = filter }
                                    .testTag("filter_chip_${filter.name}")
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // Brightness, Contrast, Sharpness Sliders
                    SliderRow(
                        label = "Brightness",
                        value = brightness,
                        valueRange = -0.5f..0.5f,
                        onValueChange = { brightness = it }
                    )
                    SliderRow(
                        label = "Contrast",
                        value = contrast,
                        valueRange = 0.5f..2.0f,
                        onValueChange = { contrast = it }
                    )
                    SliderRow(
                        label = "Sharpness",
                        value = sharpness,
                        valueRange = 0f..1f,
                        onValueChange = { sharpness = it }
                    )

                    // Quality Selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quality:", style = MaterialTheme.typography.labelLarge)
                        ImageQuality.entries.forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = q },
                                label = { Text(q.label.substringBefore(" ")) }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!overwriteOriginal) showOverwriteConfirm = true
                                else overwriteOriginal = false
                            }
                    ) {
                        Checkbox(
                            checked = overwriteOriginal,
                            onCheckedChange = { want ->
                                if (want) showOverwriteConfirm = true else overwriteOriginal = false
                            }
                        )
                        Text(
                            text = "Replace original photo file (otherwise keeps original safely)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedFilter = DocumentFilter.AUTO_ENHANCE
                                brightness = 0f
                                contrast = 1f
                                sharpness = batterySaver.defaultSharpness
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                        Button(
                            onClick = {
                                isSaving = true
                                viewModel.savePageEnhancement(
                                    pageId = pageId,
                                    filter = selectedFilter,
                                    brightness = brightness,
                                    contrast = contrast,
                                    sharpness = sharpness,
                                    rotationDegrees = rotationDegrees,
                                    quality = quality,
                                    overwriteOriginal = overwriteOriginal
                                ) {
                                    isSaving = false
                                    onDoneToSession()
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_enhancement_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Saving...")
                            } else {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Save Page")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("Overwrite Original Image?") },
            text = {
                Text("By default, ScanMate keeps your original uncropped photo safe so you can re-edit anytime. Enable this only if you want to permanently replace the original file.")
            },
            confirmButton = {
                Button(onClick = {
                    overwriteOriginal = true
                    showOverwriteConfirm = false
                }) { Text("Replace Original") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) { Text("Keep Original") }
            }
        )
    }

    if (showOcrSheet && page != null) {
        OcrResultSheet(
            selectedLanguage = ocrState.selectedLanguage,
            isRunning = ocrState.isRunning,
            ocrResult = ocrState.result,
            existingText = page.ocrText,
            onSelectLanguage = viewModel::setOcrLanguage,
            onRunOcr = { viewModel.runOcrForPage(pageId) },
            onSaveText = { text ->
                viewModel.savePageOcrText(pageId, text)
                showOcrSheet = false
            },
            onDismiss = { showOcrSheet = false }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(74.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
    }
}
