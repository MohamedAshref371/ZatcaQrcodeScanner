package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.camera_frame_error
import com.poetscorner.invoiceqr.resources.camera_start_error
import com.poetscorner.invoiceqr.ui.text.UiText
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.CameraQrDecoder
import com.poetscorner.invoiceqr.scanner.AndroidZxingQrReader
import com.poetscorner.invoiceqr.scanner.QrDeliveryGate
import com.poetscorner.invoiceqr.scanner.ScanWindow
import com.poetscorner.invoiceqr.scanner.previewCropInBuffer
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
        CameraPermissionScreen(contentPadding, topControls, requestPermission = {
            launcher.launch(Manifest.permission.CAMERA)
        }, openSettings = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()))
        })
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
        val decoder = CameraQrDecoder(AndroidZxingQrReader)
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
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val result = decoder.read(currentSettings, crop.width(), crop.height(),
                                image.imageInfo.rotationDegrees, now,
                                readFrame = { AndroidZxingQrReader.read(image, it) },
                                pixel = { x, y ->
                                    buffer.get((crop.top + y) * plane.rowStride + (crop.left + x) * plane.pixelStride)
                                })
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
    CameraOverlay(settings, zoom, onZoom, onTorch, contentPadding, topControls, focusIndicator, error,
        cameraReady = camera != null, hasTorch = camera?.cameraInfo?.hasFlashUnit() == true,
        onGuideWindow = { guideWindow = it }, onPreviewSize = { previewSize = it }) { modifier ->
        AndroidView(factory = { previewView }, modifier = modifier, update = { view ->
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
