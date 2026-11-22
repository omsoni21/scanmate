package com.example.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TealAccentBright
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CameraScannerScreen(
    folderId: String,
    appendToCurrentSession: Boolean,
    onBack: () -> Unit,
    onOpenCropPage: (String) -> Unit,
    onOpenSessionReview: () -> Unit,
    viewModel: CameraScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoCapture by viewModel.autoCaptureEnabled.collectAsStateWithLifecycle()
    val gridEnabled by viewModel.gridOverlayEnabled.collectAsStateWithLifecycle()
    val batterySaver by viewModel.batterySaverProfile.collectAsStateWithLifecycle()

    LaunchedEffect(folderId, appendToCurrentSession) {
        viewModel.prepareSessionIfNeeded(folderId, appendToCurrentSession)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importGalleryUris(uris, onImported = onOpenSessionReview)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Text("Camera Access Required", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "ScanMate uses your camera offline to detect document edges and capture crisp pages. Or import existing photos directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Camera Permission")
                }
                OutlinedButton(
                    onClick = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import From Gallery Instead")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open App Settings")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
        return
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

    val hasFlashUnit = remember(camera) {
        camera?.cameraInfo?.hasFlashUnit() == true
    }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(900)
            focusTapOffset = null
        }
    }

    LaunchedEffect(state.isTorchOn, camera, hasFlashUnit) {
        if (hasFlashUnit) {
            camera?.cameraControl?.enableTorch(state.isTorchOn)
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    fun triggerCapture() {
        val capture = imageCapture ?: return
        if (state.isCapturing) return
        viewModel.setCapturing(true)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val outFile = viewModel.createCaptureOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewModel.onPhotoCaptured(outFile) { pageId ->
                        if (!appendToCurrentSession && state.capturedPageCount == 0) {
                            onOpenCropPage(pageId)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.setCapturing(false)
                    viewModel.setError(exception.localizedMessage ?: "Failed to capture image")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val previewView = remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }

        val scaleGestureDetector = remember(camera) {
            ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta)
                    return true
                }
            })
        }

        LaunchedEffect(camera) {
            val cam = camera ?: return@LaunchedEffect
            while (true) {
                delay(3500)
                runCatching {
                    val factory = previewView.meteringPointFactory
                    val centerPoint = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                    val focusAction = FocusMeteringAction.Builder(
                        centerPoint,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                    ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    cam.cameraControl.startFocusAndMetering(focusAction)
                }
            }
        }

        LaunchedEffect(state.useFrontCamera, lifecycleOwner, batterySaver.isActive) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0

                    val previewResolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            if (batterySaver.isActive) {
                                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            } else {
                                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            }
                        )
                        .build()

                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .setResolutionSelector(previewResolutionSelector)
                        .build()
                        .apply {
                            surfaceProvider = previewView.surfaceProvider
                        }

                    val captureResolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            if (batterySaver.isActive) {
                                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            } else {
                                ResolutionStrategy(Size(3840, 2160), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            }
                        )
                        .build()

                    val captureBuilder = ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .setResolutionSelector(captureResolutionSelector)
                        .setJpegQuality(if (batterySaver.isActive) 90 else 98)
                        .setCaptureMode(
                            if (batterySaver.isActive) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        )

                    val capture = captureBuilder.build()

                    val analysisResolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            if (batterySaver.isActive) {
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                            } else {
                                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            }
                        )
                        .build()

                    val analysisBuilder = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setResolutionSelector(analysisResolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                    val analysis = analysisBuilder.build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            viewModel.analyzeFrame(imageProxy) {
                                ContextCompat.getMainExecutor(context).execute {
                                    triggerCapture()
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }

                    val selector = if (state.useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    provider.unbindAll()
                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        capture,
                        analysis
                    )

                    runCatching {
                        val factory = previewView.meteringPointFactory
                        val centerPoint = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                        val focusAction = FocusMeteringAction.Builder(
                            centerPoint,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        boundCamera.cameraControl.startFocusAndMetering(focusAction)
                    }

                    imageCapture = capture
                    camera = boundCamera
                }.onFailure {
                    viewModel.setError("Camera unavailable on this device. Use Gallery Import below.")
                }
            }, ContextCompat.getMainExecutor(context))
        }

        AndroidView(
            factory = {
                previewView.apply {
                    setOnTouchListener { v, event ->
                        scaleGestureDetector.onTouchEvent(event)
                        if (!scaleGestureDetector.isInProgress && event.action == MotionEvent.ACTION_UP) {
                            val point = meteringPointFactory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                            focusTapOffset = Offset(event.x, event.y)
                            v.performClick()
                        }
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay: Grid + Live Document Edge Polygon + Focus Ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (gridEnabled) {
                val thirdW = size.width / 3f
                val thirdH = size.height / 3f
                val gridColor = Color.White.copy(alpha = 0.24f)
                drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 1.5f)
                drawLine(gridColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 1.5f)
                drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = 1.5f)
                drawLine(gridColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = 1.5f)
            }

            state.detectedQuad?.let { quad ->
                val tl = Offset(quad.topLeft.x * size.width, quad.topLeft.y * size.height)
                val tr = Offset(quad.topRight.x * size.width, quad.topRight.y * size.height)
                val br = Offset(quad.bottomRight.x * size.width, quad.bottomRight.y * size.height)
                val bl = Offset(quad.bottomLeft.x * size.width, quad.bottomLeft.y * size.height)

                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }
                drawPath(path = path, color = TealAccentBright.copy(alpha = 0.14f))
                drawPath(
                    path = path,
                    color = TealAccentBright,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                listOf(tl, tr, br, bl).forEach { pt ->
                    drawCircle(color = ElectricBlue, radius = 12f, center = pt)
                    drawCircle(color = Color.White, radius = 7f, center = pt)
                }
            }

            focusTapOffset?.let { center ->
                drawCircle(
                    color = Color.Yellow,
                    radius = 48f,
                    center = center,
                    style = Stroke(width = 4f)
                )
            }
        }

        // Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("camera_back_button")) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = Color.White
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (hasFlashUnit) viewModel.toggleTorch()
                            else viewModel.setError("Flashlight not available on this camera")
                        },
                        modifier = Modifier.testTag("camera_flash_button")
                    ) {
                        Icon(
                            imageVector = if (state.isTorchOn && hasFlashUnit) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = "Toggle flashlight",
                            tint = if (!hasFlashUnit) Color.White.copy(alpha = 0.38f)
                                   else if (state.isTorchOn) Color.Yellow
                                   else Color.White
                        )
                    }
                    IconButton(onClick = viewModel::toggleGrid, modifier = Modifier.testTag("camera_grid_button")) {
                        Icon(
                            imageVector = if (gridEnabled) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                            contentDescription = "Toggle grid",
                            tint = if (gridEnabled) TealAccentBright else Color.White
                        )
                    }
                    IconButton(onClick = viewModel::toggleAutoCapture, modifier = Modifier.testTag("camera_auto_button")) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Toggle auto capture",
                            tint = if (autoCapture) TealAccentBright else Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = viewModel::toggleCameraFacing, modifier = Modifier.testTag("camera_switch_button")) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = "Switch camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Status Pill (Auto-detect / Text Detection / Error + Battery Saver indicator)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusText = when {
                state.errorMessage != null -> state.errorMessage!!
                autoCapture && (state.detectedQuad != null || state.hasTextDetected) -> {
                    if (state.hasTextDetected && state.detectedTextSnippet != null) {
                        "Text: \"${state.detectedTextSnippet}\" • Auto-Capturing..."
                    } else {
                        "Document detected • Hold steady for auto-capture"
                    }
                }
                autoCapture -> "Auto-Capture ON • Align document or text"
                state.hasTextDetected && state.detectedTextSnippet != null -> {
                    "Text Detected: \"${state.detectedTextSnippet}\""
                }
                state.detectedQuad != null -> "Edges detected • Tap shutter to scan"
                else -> "Align document & text • High clarity active"
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            if (state.hasTextDetected) {
                Surface(
                    shape = CircleShape,
                    color = TealAccentBright.copy(alpha = 0.92f),
                    modifier = Modifier.testTag("camera_text_detected_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Text auto detected",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Auto Text Detected (${state.textBlockCount} blocks)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (batterySaver.isActive) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF92400E).copy(alpha = 0.88f),
                    modifier = Modifier.testTag("camera_battery_saver_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = "Battery saver active",
                            tint = Color(0xFFFDE68A),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Battery Saver • ${batterySaver.batteryPercent}% • Reduced scan res",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Bottom Capture Dock
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Import
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(8.dp)
                        .testTag("camera_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = "Import image from gallery",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (state.isCapturing) TealAccentBright else Color.White)
                        .clickable(enabled = !state.isCapturing) { triggerCapture() }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = Color.White
                        )
                    }
                }

                // Review / Done Multi-Page Pill
                AnimatedVisibility(
                    visible = state.capturedPageCount > 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TealAccentBright,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenSessionReview)
                            .testTag("camera_review_pages_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${state.capturedPageCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Review scanned pages",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (state.capturedPageCount == 0) {
                    Spacer(Modifier.width(56.dp))
                }
            }
        }
    }
}
