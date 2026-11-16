package com.poetscorner.invoiceqr.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poetscorner.invoiceqr.sharedui.InvoiceScannerFooter
import com.poetscorner.invoiceqr.sharedui.InvoiceScannerViewport
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.sharedui.InvoiceScanResolution
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.scanner.decoder
import com.poetscorner.invoiceqr.scanner.reader
import com.poetscorner.invoiceqr.scanner.MultiFrameQrRecovery
import com.poetscorner.invoiceqr.scanner.ScanWindow
import com.poetscorner.invoiceqr.scanner.previewCropInBuffer
import com.poetscorner.invoiceqr.scanner.QrDeliveryGate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import zxingcpp.BarcodeReader

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
    DisposableEffect(owner, settings.resolution, settings.scanEngine) {
        val executor = Executors.newSingleThreadExecutor()
        val decoder = settings.scanEngine.decoder()
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
                val resolution = InvoiceScanResolution.fromPreference(settings.resolution)
                val size = Size(resolution.width, resolution.height)
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
                            val result = if (currentSettings.multiFrameRecovery &&
                                currentSettings.scanEngine == ScanEngine.ZXING) {
                                val observed = currentSettings.reader(maxSymbols = 3, returnErrors = true).read(image)
                                    .filter { it.format == BarcodeReader.Format.QR_CODE ||
                                        it.format == BarcodeReader.Format.QR_CODE_MODEL_2 }
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
                                        ?.takeIf { it.error?.type == BarcodeReader.ErrorType.CHECKSUM ||
                                            it.error?.type == BarcodeReader.ErrorType.FORMAT }?.position
                                    runCatching {
                                        recovery.offer(image, position, now, cropEnabled) { bitmap ->
                                            currentSettings.reader(maxSymbols = 2).read(bitmap)
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
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, a)
                boundPreview = pv; boundAnalysis = a
                error = null
            } catch (_: Exception) { error = UiText(R.string.camera_start_error) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true; analysis?.clearAnalyzer()
            val uses = listOfNotNull(preview, analysis).toTypedArray()
            if (uses.isNotEmpty()) provider?.unbind(*uses)
            // Close after the in-flight frame finishes, including any asynchronous ML Kit task.
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
    InvoiceScannerViewport(
        cropEnabled = settings.cropEnabled,
        contentPadding = contentPadding,
        topControls = topControls,
        bottomControls = {
            InvoiceScannerFooter(
                hint = stringResource(if (settings.cropEnabled) R.string.camera_hint else R.string.camera_hint_full_frame),
                zoomLabel = stringResource(R.string.zoom),
                torchLabel = stringResource(if (settings.torch) R.string.torch_off else R.string.torch),
                zoom = zoom, onZoom = onZoom, onTorch = onTorch,
                zoomEnabled = camera != null, torchEnabled = camera?.cameraInfo?.hasFlashUnit() == true,
                status = {
                    error?.let {
                        Surface(color = Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium) {
                            Text(it.localized(), color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                },
            )
        },
        previewOverlay = {
            focusIndicator?.let { center ->
                Canvas(Modifier.fillMaxSize().testTag("camera_focus_indicator")) {
                    drawCircle(Color.Black.copy(alpha = 0.5f), 28.dp.toPx(), center,
                        style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, 28.dp.toPx(), center,
                        style = Stroke(2.dp.toPx()))
                }
            }
        },
    ) { guide, _, _ ->
        SideEffect { guideWindow = ScanWindow(guide.left, guide.top, guide.size) }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().onSizeChanged { previewSize = it }, update = { view ->
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
    }
}
