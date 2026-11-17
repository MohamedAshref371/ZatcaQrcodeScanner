package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.camera_frame_error
import com.poetscorner.invoiceqr.resources.camera_start_error
import com.poetscorner.invoiceqr.resources.ic_scan
import com.poetscorner.invoiceqr.resources.ic_flash
import androidx.compose.ui.res.stringResource
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.ui.text.UiText
import com.poetscorner.invoiceqr.ui.text.localized
import android.Manifest
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.scanner.AndroidZxingQrReader
import com.poetscorner.invoiceqr.scanner.MultiFrameQrRecovery
import com.poetscorner.invoiceqr.scanner.QrDeliveryGate
import com.poetscorner.invoiceqr.scanner.QrFormat
import com.poetscorner.invoiceqr.scanner.QrReadError
import com.poetscorner.invoiceqr.scanner.ScanWindow
import com.poetscorner.invoiceqr.scanner.ZxingDecoder
import com.poetscorner.invoiceqr.scanner.previewCropInBuffer
import com.poetscorner.invoiceqr.scanner.qrReadOptions
import com.poetscorner.invoiceqr.scanner.scanGuideWindow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable
fun CameraScanner(settings: ScannerSettings, continuous: Boolean, zoom: Float, onZoom: (Float) -> Unit,
    onScan: suspend (String) -> Boolean, onTorch: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(), topControls: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }
    if (!permission) {
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(12.dp)) {
        topControls()
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Res.drawable.ic_scan)
            Text(stringResource(R.string.camera_permission_note))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.camera_allow)) }
            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text(stringResource(R.string.permission_settings)) }
        } } } }
        return
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var guideWindow by remember { mutableStateOf<ScanWindow?>(null) }
    val currentGuideWindow by rememberUpdatedState(guideWindow)
    var displayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    var boundAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }
    // 180-degree rotations need an explicit update even when the view size is unchanged.
    DisposableEffect(previewView) {
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        fun updateRotation() { previewView.display?.let { displayRotation = it.rotation } }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) { updateRotation() }
            override fun onDisplayAdded(displayId: Int) { updateRotation() }
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        displays.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        previewView.post { updateRotation() }
        onDispose { displays.unregisterDisplayListener(listener) }
    }
    LaunchedEffect(boundAnalysis, boundPreview, displayRotation) {
        boundAnalysis?.targetRotation = displayRotation
        boundPreview?.targetRotation = displayRotation
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusIndicator by remember(camera, previewSize) { mutableStateOf<Offset?>(null) }
    var focusTapId by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusTapId, camera, previewSize) {
        delay(1000)
        focusIndicator = null
    }
    var error by remember { mutableStateOf<UiText?>(null) }
    val currentSettings by rememberUpdatedState(settings)
    val currentOnScan by rememberUpdatedState(onScan)
    val currentContinuous by rememberUpdatedState(continuous)
    val scope = rememberCoroutineScope()
    val deliveryGate by rememberUpdatedState(remember(continuous) { QrDeliveryGate(continuous) })
    // Switching scan mode updates delivery rules without rebinding the camera.
    DisposableEffect(owner, settings.resolution) {
        val executor = Executors.newSingleThreadExecutor()
        val decoder = ZxingDecoder()
        val recovery = MultiFrameQrRecovery()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val delivered = AtomicBoolean(false)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var preview: Preview? = null
        providerFuture.addListener({
            if (!disposed) try {
                val p = providerFuture.get(); provider = p
                val size = when (settings.resolution) {
                    "SD" -> Size(854, 480)
                    "FHD" -> Size(1920, 1080)
                    "QHD" -> Size(2560, 1440)
                    "UHD" -> Size(3840, 2160)
                    else -> Size(1280, 720)
                }
                val selector = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
                val rotation = previewView.display?.rotation ?: displayRotation
                val pv = Preview.Builder().setTargetRotation(rotation).build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }; preview = pv
                val a = ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); analysis = a
                var last = 0L
                a.setAnalyzer(executor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (!delivered.get() && now - last >= currentSettings.intervalMs) {
                            last = now
                            // Map the live view through sensor coordinates. This keeps the crop
                            // aligned after resizing/rotation without rebinding a fixed ViewPort.
                            val cropEnabled = currentSettings.cropEnabled
                            val cropStarted = if (CameraFrameTiming.enabled) SystemClock.elapsedRealtimeNanos() else 0L
                            val crop = runBlocking(Dispatchers.Main) {
                                val sensorToView = previewView.sensorToViewTransform ?: return@runBlocking null
                                val viewToSensor = Matrix()
                                if (!sensorToView.invert(viewToSensor)) return@runBlocking null
                                val window = currentGuideWindow
                                val rect = if (cropEnabled) {
                                    if (window == null) return@runBlocking null
                                    RectF(window.left.toFloat(), window.top.toFloat(),
                                        (window.left + window.size).toFloat(), (window.top + window.size).toFloat())
                                } else RectF(0f, 0f, previewView.width.toFloat(), previewView.height.toFloat())
                                previewCropInBuffer(viewToSensor, image.imageInfo.sensorToBufferTransformMatrix,
                                    rect, image.cropRect)
                            } ?: return@setAnalyzer
                            val cropFinished = if (cropStarted != 0L) SystemClock.elapsedRealtimeNanos() else 0L
                            image.setCropRect(crop)
                            val result = if (currentSettings.multiFrameRecovery) {
                                val observed = AndroidZxingQrReader.read(image,
                                    currentSettings.qrReadOptions(maxSymbols = 3, returnErrors = true))
                                    .filter { it.format == QrFormat.QR_CODE || it.format == QrFormat.MODEL_2 }
                                val valid = observed.firstOrNull { it.error == null && it.text != null }?.text
                                if (valid != null) {
                                    recovery.reset()
                                    valid
                                } else if (observed.firstOrNull()?.let { first ->
                                    observed.any { !recovery.samePosition(first.position, it.position) }
                                } == true) {
                                    recovery.reset()
                                    null
                                } else {
                                    val position = observed.firstOrNull()
                                        ?.takeIf { it.error == QrReadError.CHECKSUM ||
                                            it.error == QrReadError.FORMAT }?.position
                                    runCatching {
                                        val plane = image.planes[0]
                                        val buffer = plane.buffer
                                        recovery.offer(crop.width(), crop.height(), image.imageInfo.rotationDegrees,
                                            position, now, cropEnabled, pixel = { x, y ->
                                                buffer.get((crop.top + y) * plane.rowStride + (crop.left + x) * plane.pixelStride)
                                            }) { luma ->
                                            AndroidZxingQrReader.read(luma,
                                                currentSettings.qrReadOptions(maxSymbols = 2))
                                                .filter { it.error == null && it.text != null }
                                                .singleOrNull()?.text
                                        }
                                    }.onFailure { recovery.reset() }.getOrNull()
                                        ?.takeIf { ZatcaParser.parse(it).error == null }
                                }
                            } else {
                                recovery.reset()
                                decoder.read(image, currentSettings)
                            }
                            if (cropStarted != 0L) CameraFrameTiming.record(CameraFrameTiming.Sample(
                                cropEnabled, image.width, image.height,
                                cropFinished - cropStarted,
                                SystemClock.elapsedRealtimeNanos() - cropFinished,
                                result != null))
                            val gate = deliveryGate
                            val shouldDeliver = synchronized(gate) { gate.shouldDeliver(result, now) }
                            if (result != null && shouldDeliver
                                && delivered.compareAndSet(false, true)) {
                                scope.launch {
                                    var accepted = false
                                    val scanWasContinuous = currentContinuous
                                    try {
                                        if (!disposed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                            accepted = currentOnScan(result)
                                        }
                                    }
                                    finally {
                                        if (!accepted || scanWasContinuous || currentContinuous) delivered.set(false)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        ContextCompat.getMainExecutor(context).execute { if (!disposed) error = UiText(Res.string.camera_frame_error) }
                    } finally { image.close() }
                }
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, a)
                boundPreview = pv; boundAnalysis = a
                error = null
            } catch (_: Exception) { error = UiText(Res.string.camera_start_error) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true; analysis?.clearAnalyzer()
            val uses = listOfNotNull(preview, analysis).toTypedArray()
            if (uses.isNotEmpty()) provider?.unbind(*uses)
            // Close after the in-flight frame finishes.
            executor.execute { decoder.close() }
            executor.shutdown(); camera = null; boundPreview = null; boundAnalysis = null
        }
    }
    LaunchedEffect(camera, settings.torch) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) camera?.cameraControl?.enableTorch(settings.torch)
    }
    val lifecycleState by owner.lifecycle.currentStateAsState()
    LaunchedEffect(camera, zoom, lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val density = LocalDensity.current
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        var headerHeight by remember { mutableIntStateOf(0) }
        var footerHeight by remember { mutableIntStateOf(0) }
        val margin = with(density) { 12.dp.roundToPx() }
        val leftInset = with(density) { contentPadding.calculateLeftPadding(LocalLayoutDirection.current).roundToPx() }
        val rightInset = with(density) { contentPadding.calculateRightPadding(LocalLayoutDirection.current).roundToPx() }
        val sideControls = if (landscape) ((constraints.maxWidth - leftInset - rightInset - margin * 2) * 0.32f).toInt() + margin else 0
        val window = scanGuideWindow(constraints.maxWidth.coerceAtLeast(1), constraints.maxHeight.coerceAtLeast(1),
            leftInset + margin + if (!rtl) sideControls else 0,
            with(density) { contentPadding.calculateTopPadding().roundToPx() } + margin + if (landscape) 0 else headerHeight + margin,
            rightInset + margin + if (rtl) sideControls else 0,
            with(density) { contentPadding.calculateBottomPadding().roundToPx() } + margin + if (landscape) 0 else footerHeight + margin)
        SideEffect { guideWindow = window }
        Box(Modifier.fillMaxSize().testTag("camera_preview").clipToBounds().onSizeChanged { previewSize = it }) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize(), update = { view ->
                view.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        camera?.let { activeCamera ->
                            val point = view.meteringPointFactory.createPoint(event.x, event.y)
                            activeCamera.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(point)
                                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                                    .build())
                            focusIndicator = Offset(event.x, event.y)
                            focusTapId++
                        }
                        view.performClick()
                    }
                    true
                }
            })
            if (settings.cropEnabled) Canvas(Modifier.fillMaxSize().testTag("camera_crop_guide")) {
                if (size.width < 1 || size.height < 1) return@Canvas
                val left = window.left.toFloat()
                val top = window.top.toFloat()
                val side = window.size.toFloat()
                val radius = CornerRadius(20.dp.toPx())
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(left, top, left + side, top + side, radius))
                }
                drawPath(shade, Color.Black.copy(alpha = 0.5f))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(left, top), ComposeSize(side, side), radius, style = Stroke(1.dp.toPx()))
            }
            focusIndicator?.let { center ->
                Canvas(Modifier.fillMaxSize().testTag("camera_focus_indicator")) {
                    drawCircle(Color.Black.copy(alpha = 0.5f), 28.dp.toPx(), center,
                        style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, 28.dp.toPx(), center,
                        style = Stroke(2.dp.toPx()))
                }
            }
        }
        // Only the overlays respect app bars; the preview fills the whole screen.
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(12.dp)) {
            Column(Modifier.align(Alignment.TopStart).fillMaxHeight()
                .then(if (landscape) Modifier.fillMaxWidth(0.32f).verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.onSizeChanged { headerHeight = it.height }) { topControls() }
                if (!landscape) Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().onSizeChanged { footerHeight = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(if (settings.cropEnabled) R.string.camera_hint else R.string.camera_hint_full_frame),
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = TextAlign.Center)
                    }
                    error?.let {
                        Surface(color = Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium) {
                            Text(it.localized(), color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
                        shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.zoom), style = MaterialTheme.typography.labelMedium)
                            Slider(zoom, onValueChange = onZoom,
                                modifier = Modifier.weight(1f), enabled = camera != null)
                            FilledTonalIconButton(onClick = onTorch, enabled = camera?.cameraInfo?.hasFlashUnit() == true) {
                                AppIcon(Res.drawable.ic_flash, description = stringResource(if (settings.torch) R.string.torch_off else R.string.torch))
                            }
                        }
                    }
                }
            }
        }
    }
}
