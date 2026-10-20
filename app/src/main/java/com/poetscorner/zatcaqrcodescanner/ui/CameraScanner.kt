package com.poetscorner.zatcaqrcodescanner.ui

import androidx.compose.ui.res.stringResource
import com.poetscorner.zatcaqrcodescanner.R
import com.poetscorner.zatcaqrcodescanner.ui.text.UiText
import com.poetscorner.zatcaqrcodescanner.ui.text.localized
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poetscorner.zatcaqrcodescanner.data.ScannerSettings
import com.poetscorner.zatcaqrcodescanner.scanner.reader
import com.poetscorner.zatcaqrcodescanner.scanner.scanWindow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

@Composable
fun CameraScanner(settings: ScannerSettings, onScan: suspend (String) -> Boolean, onTorch: () -> Unit) {
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
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.camera_permission_note))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.camera_allow)) }
            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.permission_settings)) }
        }
        return
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var zoom by remember { mutableFloatStateOf(0f) }
    val currentSettings by rememberUpdatedState(settings)
    val currentOnScan by rememberUpdatedState(onScan)
    val scope = rememberCoroutineScope()
    DisposableEffect(owner, settings.resolution, previewSize) {
        if (previewSize.width == 0 || previewSize.height == 0) return@DisposableEffect onDispose { }
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val delivered = AtomicBoolean(false)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var preview: Preview? = null
        providerFuture.addListener({
            if (!disposed) try {
                val p = providerFuture.get(); provider = p
                val size = when(settings.resolution) { "FHD" -> Size(1920,1080); "QHD" -> Size(2560,1440); else -> Size(1280,720) }
                val selector = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
                val pv = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }; preview = pv
                val a = ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); analysis = a
                var last = 0L
                var lastCandidate: String? = null
                var lastCandidateAt = 0L
                a.setAnalyzer(executor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (!delivered.get() && now - last >= currentSettings.intervalMs) {
                            last = now
                            // The shared ViewPort maps this crop to the preview's visible sensor area.
                            // ZXing reads ImageProxy.cropRect, so pixels outside the guide are never scanned.
                            val visible = image.cropRect
                            val window = scanWindow(visible.width(), visible.height())
                            val left = visible.left + window.left
                            val top = visible.top + window.top
                            image.setCropRect(Rect(left, top, left + window.size, top + window.size))
                            val result = currentSettings.reader().read(image).firstOrNull { it.error == null && it.text != null }
                            // Suppress repeated warnings for the same rejected QR while allowing a different one immediately.
                            if (result != null && (result.text != lastCandidate || now - lastCandidateAt >= 4000L)
                                && delivered.compareAndSet(false, true)) {
                                lastCandidate = result.text
                                lastCandidateAt = now
                                scope.launch {
                                    var accepted = false
                                    try { if (!disposed) accepted = currentOnScan(result.text!!) }
                                    finally { if (!accepted) delivered.set(false) }
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
            executor.shutdown(); camera = null
        }
    }
    LaunchedEffect(camera, settings.torch) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) camera?.cameraControl?.enableTorch(settings.torch)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { previewSize = it }) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize(), update = { view ->
                view.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (currentSettings.tapFocus) {
                            val point = view.meteringPointFactory.createPoint(event.x, event.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                        view.performClick()
                    }
                    true
                }
            })
            Canvas(Modifier.fillMaxSize()) {
                if (size.width < 1 || size.height < 1) return@Canvas
                val window = scanWindow(size.width.toInt(), size.height.toInt())
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
                drawRoundRect(Color.White, Offset(left, top), ComposeSize(side, side), radius, style = Stroke(3.dp.toPx()))
            }
            Text(stringResource(R.string.camera_hint), Modifier.align(Alignment.TopCenter).padding(20.dp),
                color = Color.White, textAlign = TextAlign.Center)
            error?.let { Text(it.localized(), color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) }
        }
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.zoom))
            Slider(zoom, onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) }, modifier = Modifier.weight(1f), enabled = camera != null)
            TextButton(onClick = onTorch, enabled = camera?.cameraInfo?.hasFlashUnit() == true) { Text(if(settings.torch) stringResource(R.string.torch_off) else stringResource(R.string.torch)) }
        }
    }
}
