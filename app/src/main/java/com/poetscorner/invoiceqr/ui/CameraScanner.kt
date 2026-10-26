package com.poetscorner.invoiceqr.ui

import androidx.compose.ui.res.stringResource
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.ui.text.UiText
import com.poetscorner.invoiceqr.ui.text.localized
import android.Manifest
import android.graphics.Rect
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import android.util.Rational
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.decoder
import com.poetscorner.invoiceqr.scanner.scanWindow
import com.poetscorner.invoiceqr.scanner.QrDeliveryGate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun CameraScanner(settings: ScannerSettings, continuous: Boolean, onScan: suspend (String) -> Boolean, onTorch: () -> Unit,
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
            IconTile(R.drawable.ic_scan)
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
    var scanFraction by remember { mutableFloatStateOf(0.8f) }
    val currentScanFraction by rememberUpdatedState(scanFraction)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusIndicator by remember(camera, previewSize) { mutableStateOf<Offset?>(null) }
    var focusTapId by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusTapId, camera, previewSize) {
        delay(1000)
        focusIndicator = null
    }
    var error by remember { mutableStateOf<UiText?>(null) }
    var zoom by remember { mutableFloatStateOf(0f) }
    val currentSettings by rememberUpdatedState(settings)
    val currentOnScan by rememberUpdatedState(onScan)
    val currentContinuous by rememberUpdatedState(continuous)
    val scope = rememberCoroutineScope()
    val deliveryGate by rememberUpdatedState(remember(continuous) { QrDeliveryGate(continuous) })
    // Switching scan mode updates delivery rules without rebinding the camera.
    DisposableEffect(owner, settings.resolution, settings.scanEngine, previewSize) {
        if (previewSize.width == 0 || previewSize.height == 0) return@DisposableEffect onDispose { }
        val executor = Executors.newSingleThreadExecutor()
        val decoder = settings.scanEngine.decoder()
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
                val pv = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }; preview = pv
                val a = ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); analysis = a
                var last = 0L
                a.setAnalyzer(executor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (!delivered.get() && now - last >= currentSettings.intervalMs) {
                            last = now
                            // Without the guide, retain the whole visible ViewPort for both engines.
                            if (currentSettings.cropEnabled) {
                                val visible = image.cropRect
                                val window = scanWindow(visible.width(), visible.height(), currentScanFraction)
                                val left = visible.left + window.left
                                val top = visible.top + window.top
                                image.setCropRect(Rect(left, top, left + window.size, top + window.size))
                            }
                            val result = decoder.read(image, currentSettings)
                            val shouldDeliver = deliveryGate.shouldDeliver(result, now)
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
                        ContextCompat.getMainExecutor(context).execute { if (!disposed) error = UiText(R.string.camera_frame_error) }
                    } finally { image.close() }
                }
                val viewPort = ViewPort.Builder(Rational(previewSize.width, previewSize.height),
                    previewView.display?.rotation ?: Surface.ROTATION_0).setScaleType(ViewPort.FILL_CENTER).build()
                val group = UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(pv).addUseCase(a).build()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                zoom = 0f; error = null
            } catch (_: Exception) { error = UiText(R.string.camera_start_error) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true; analysis?.clearAnalyzer()
            val uses = listOfNotNull(preview, analysis).toTypedArray()
            if (uses.isNotEmpty()) provider?.unbind(*uses)
            // Close after the in-flight frame finishes, including any asynchronous ML Kit task.
            executor.execute { decoder.close() }
            executor.shutdown(); camera = null
        }
    }
    LaunchedEffect(camera, settings.torch) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) camera?.cameraControl?.enableTorch(settings.torch)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val density = LocalDensity.current
        var headerHeight by remember { mutableIntStateOf(0) }
        var footerHeight by remember { mutableIntStateOf(0) }
        val topInset = with(density) { (contentPadding.calculateTopPadding() + 24.dp).toPx() }
        val bottomInset = with(density) { (contentPadding.calculateBottomPadding() + 24.dp).toPx() }
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val verticalClearance = 2f * minOf(
            height / 2f - topInset - (if (landscape) 0 else headerHeight),
            height / 2f - bottomInset - (if (landscape) 0 else footerHeight))
        val horizontalClearance = if (landscape) width * 0.36f - with(density) { 48.dp.toPx() } else width
        val guideFraction = (minOf(verticalClearance, horizontalClearance) / minOf(width, height)).coerceIn(0.01f, 0.8f)
        SideEffect { scanFraction = guideFraction }
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
                val window = scanWindow(size.width.toInt(), size.height.toInt(), guideFraction)
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
        // Only the overlays respect app bars; the preview and its ViewPort use the whole screen.
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
                            Slider(zoom, onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) },
                                modifier = Modifier.weight(1f), enabled = camera != null)
                            FilledTonalIconButton(onClick = onTorch, enabled = camera?.cameraInfo?.hasFlashUnit() == true) {
                                AppIcon(R.drawable.ic_flash, description = stringResource(if (settings.torch) R.string.torch_off else R.string.torch))
                            }
                        }
                    }
                }
            }
        }
    }
}
