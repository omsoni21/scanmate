package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.processing.DocumentEdgeDetector
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.QuadPoints
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TealAccentBright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

private enum class DragCorner { TL, TR, BR, BL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageCropScreen(
    pageId: String,
    fileStorage: LocalFileStorage,
    onBack: () -> Unit,
    onAppliedGoToEditor: (String) -> Unit,
    viewModel: ScanSessionViewModel = hiltViewModel()
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val page = session.pages.find { it.id == pageId }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quad by remember(pageId) { mutableStateOf(page?.cropCorners ?: QuadPoints.DEFAULT_INSET) }
    var rotationDegrees by remember(pageId) { mutableIntStateOf(page?.rotationDegrees ?: 0) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(page?.originalImagePath) {
        val path = page?.originalImagePath ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { fileStorage.loadBitmap(path, 1600) }
        originalBitmap = loaded
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust Document Corners") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        modifier = Modifier.testTag("crop_rotate_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val bmp = originalBitmap ?: return@OutlinedButton
                                val detected = DocumentEdgeDetector.detectFromBitmap(bmp)
                                quad = detected.quad
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto Detect")
                        }
                        OutlinedButton(
                            onClick = { quad = QuadPoints.FULL_FRAME },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Full Frame")
                        }
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            viewModel.savePageCropAndRotation(
                                pageId = pageId,
                                quad = quad,
                                rotationDegrees = rotationDegrees
                            ) {
                                isSaving = false
                                onAppliedGoToEditor(pageId)
                            }
                        },
                        enabled = !isSaving && originalBitmap != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apply_crop_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Correcting Perspective...")
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Crop & Enhance")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val bmp = originalBitmap
        if (bmp == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0B1120))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()
                val scale = min(containerW / bmp.width, containerH / bmp.height)
                val imgW = bmp.width * scale
                val imgH = bmp.height * scale
                val offsetX = (containerW - imgW) / 2f
                val offsetY = (containerH - imgH) / 2f

                var activeCorner by remember { mutableStateOf<DragCorner?>(null) }

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured document for corner adjustment",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(imgW, imgH, offsetX, offsetY) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val corners = mapOf(
                                        DragCorner.TL to Offset(offsetX + quad.topLeft.x * imgW, offsetY + quad.topLeft.y * imgH),
                                        DragCorner.TR to Offset(offsetX + quad.topRight.x * imgW, offsetY + quad.topRight.y * imgH),
                                        DragCorner.BR to Offset(offsetX + quad.bottomRight.x * imgW, offsetY + quad.bottomRight.y * imgH),
                                        DragCorner.BL to Offset(offsetX + quad.bottomLeft.x * imgW, offsetY + quad.bottomLeft.y * imgH)
                                    )
                                    val nearest = corners.minByOrNull { (_, pt) ->
                                        hypot((pt.x - start.x).toDouble(), (pt.y - start.y).toDouble())
                                    }
                                    if (nearest != null) {
                                        val dist = hypot((nearest.value.x - start.x).toDouble(), (nearest.value.y - start.y).toDouble())
                                        activeCorner = if (dist < 140.0) nearest.key else null
                                    }
                                },
                                onDragEnd = { activeCorner = null },
                                onDragCancel = { activeCorner = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val corner = activeCorner ?: return@detectDragGestures
                                    val dx = dragAmount.x / imgW
                                    val dy = dragAmount.y / imgH
                                    quad = when (corner) {
                                        DragCorner.TL -> quad.copy(
                                            topLeft = NormalizedPoint(
                                                (quad.topLeft.x + dx).coerceIn(0f, 0.92f),
                                                (quad.topLeft.y + dy).coerceIn(0f, 0.92f)
                                            )
                                        )
                                        DragCorner.TR -> quad.copy(
                                            topRight = NormalizedPoint(
                                                (quad.topRight.x + dx).coerceIn(0.08f, 1f),
                                                (quad.topRight.y + dy).coerceIn(0f, 0.92f)
                                            )
                                        )
                                        DragCorner.BR -> quad.copy(
                                            bottomRight = NormalizedPoint(
                                                (quad.bottomRight.x + dx).coerceIn(0.08f, 1f),
                                                (quad.bottomRight.y + dy).coerceIn(0.08f, 1f)
                                            )
                                        )
                                        DragCorner.BL -> quad.copy(
                                            bottomLeft = NormalizedPoint(
                                                (quad.bottomLeft.x + dx).coerceIn(0f, 0.92f),
                                                (quad.bottomLeft.y + dy).coerceIn(0.08f, 1f)
                                            )
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    val tl = Offset(offsetX + quad.topLeft.x * imgW, offsetY + quad.topLeft.y * imgH)
                    val tr = Offset(offsetX + quad.topRight.x * imgW, offsetY + quad.topRight.y * imgH)
                    val br = Offset(offsetX + quad.bottomRight.x * imgW, offsetY + quad.bottomRight.y * imgH)
                    val bl = Offset(offsetX + quad.bottomLeft.x * imgW, offsetY + quad.bottomLeft.y * imgH)

                    val path = Path().apply {
                        moveTo(tl.x, tl.y)
                        lineTo(tr.x, tr.y)
                        lineTo(br.x, br.y)
                        lineTo(bl.x, bl.y)
                        close()
                    }
                    drawPath(path = path, color = TealAccentBright.copy(alpha = 0.16f))
                    drawPath(path = path, color = TealAccentBright, style = Stroke(width = 6f))

                    listOf(
                        DragCorner.TL to tl,
                        DragCorner.TR to tr,
                        DragCorner.BR to br,
                        DragCorner.BL to bl
                    ).forEach { (tag, pt) ->
                        val isActive = activeCorner == tag
                        drawCircle(
                            color = ElectricBlue.copy(alpha = if (isActive) 0.4f else 0.22f),
                            radius = if (isActive) 44f else 32f,
                            center = pt
                        )
                        drawCircle(color = ElectricBlue, radius = 20f, center = pt)
                        drawCircle(color = Color.White, radius = 12f, center = pt)
                    }
                }
            }
        }
    }
}
