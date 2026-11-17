@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.camera_frame_error
import com.poetscorner.invoiceqr.resources.camera_start_error
import com.poetscorner.invoiceqr.scanner.*
import com.poetscorner.invoiceqr.ui.text.UiText
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import platform.AVFoundation.*
import platform.CoreGraphics.CGPointMake
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.*
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

@Composable
fun IosCameraScanner(settings: ScannerSettings, continuous: Boolean, zoom: Float, onZoom: (Float) -> Unit,
    onScan: suspend (String) -> Boolean, onTorch: () -> Unit, contentPadding: PaddingValues,
    controls: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    var allowed by remember { mutableStateOf(false) }
    val requestPermission = {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            dispatch_async(dispatch_get_main_queue()) { allowed = granted }
        }
    }
    LaunchedEffect(active) {
        if (active) {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            allowed = status == AVAuthorizationStatusAuthorized
            if (status == AVAuthorizationStatusNotDetermined) requestPermission()
        }
    }
    if (!allowed) {
        CameraPermissionScreen(contentPadding, controls, requestPermission) {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
            }
        }
        return
    }

    val currentOnScan by rememberUpdatedState(onScan)
    var ready by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val camera = remember {
        IosCaptureSession(
            onStatus = { ok, torch ->
                ready = ok
                hasTorch = torch
                if (ok) error = null
            },
            onError = { start -> error = UiText(if (start) Res.string.camera_start_error else Res.string.camera_frame_error) },
            onRead = { raw, completed ->
                scope.launch {
                    var accepted = false
                    try {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) accepted = currentOnScan(raw)
                    } finally {
                        completed(accepted)
                    }
                }
            }
        )
    }
    DisposableEffect(camera, active, settings.resolution) {
        if (active) camera.start(settings.resolution)
        onDispose { camera.stop() }
    }
    LaunchedEffect(camera, zoom, settings.torch, ready) {
        if (ready) camera.updateControls(zoom, settings.torch)
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var guide by remember { mutableStateOf<ScanWindow?>(null) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) { delay(1000); focus = null }
    SideEffect { camera.controls.value = FrameControls(settings, continuous, viewSize, guide) }
    val density = LocalDensity.current.density
    CameraOverlay(settings, zoom, onZoom, onTorch, contentPadding, controls, focus, error, ready, hasTorch,
        onGuideWindow = { guide = it }, onPreviewSize = { viewSize = it }) { modifier ->
        Box(modifier.pointerInput(camera, density, ready) {
            detectTapGestures { point ->
                if (ready) {
                    focus = point
                    taps++
                    camera.focus(point.x / density.toDouble(), point.y / density.toDouble())
                }
            }
        }) {
            UIKitView(factory = { camera.view }, modifier = Modifier.fillMaxSize(),
                properties = androidx.compose.ui.viewinterop.UIKitInteropProperties(interactionMode = null))
        }
    }
}

private data class FrameControls(val settings: ScannerSettings, val continuous: Boolean, val size: IntSize,
    val guide: ScanWindow?)

private class IosCaptureSession(
    private val onStatus: (Boolean, Boolean) -> Unit,
    private val onError: (Boolean) -> Unit,
    private val onRead: (String, (Boolean) -> Unit) -> Unit,
) {
    val controls = MutableStateFlow<FrameControls?>(null)
    private val queue = dispatch_queue_create("com.poetscorner.invoiceqr.camera", null)
    private val session = AVCaptureSession()
    private val output = AVCaptureVideoDataOutput()
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }
    val view: UIView = object : UIView() {
        override fun layoutSubviews() {
            super.layoutSubviews()
            preview.frame = bounds
            val orientation = when (window?.windowScene?.interfaceOrientation) {
                UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
                UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
                UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
                else -> AVCaptureVideoOrientationPortrait
            }
            preview.connection?.let { if (it.isVideoOrientationSupported()) it.videoOrientation = orientation }
            dispatch_async(queue) {
                rotation = orientation
                applyRotation()
            }
        }
    }.apply { layer.addSublayer(preview) }

    private var rotation = AVCaptureVideoOrientationPortrait
    private var device: AVCaptureDevice? = null
    private var lastFrameAt = Long.MIN_VALUE
    private var continuous = false
    private var delivered = false
    private var sessionGeneration = 0
    private var gate = QrDeliveryGate(false)
    private var decoder = CameraQrDecoder(IosZxingQrReader)
    private val delegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(output: AVCaptureOutput, didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection) {
            val frame = didOutputSampleBuffer?.let { CMSampleBufferGetImageBuffer(it) } ?: return
            val config = controls.value ?: return
            if (delivered) return
            val now = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
            if (lastFrameAt != Long.MIN_VALUE && now - lastFrameAt < config.settings.intervalMs) return
            lastFrameAt = now
            try {
                val raw = read(frame, config, now)
                if (continuous != config.continuous) {
                    continuous = config.continuous
                    gate = QrDeliveryGate(continuous)
                }
                val shouldDeliver = gate.shouldDeliver(raw, now)
                if (raw == null || !shouldDeliver) return
                delivered = true
                val generation = sessionGeneration
                val wasContinuous = continuous
                dispatch_async(dispatch_get_main_queue()) {
                    onRead(raw) { accepted ->
                        dispatch_async(queue) {
                            if (generation == sessionGeneration &&
                                (!accepted || wasContinuous || controls.value?.continuous == true)) delivered = false
                        }
                    }
                }
            } catch (_: Exception) {
                dispatch_async(dispatch_get_main_queue()) { onError(false) }
            }
        }
    }

    fun start(resolution: String) = dispatch_async(queue) {
        if (device == null) {
            val selected = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            val input = selected?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
            if (input == null || !session.canAddInput(input) || !session.canAddOutput(output)) {
                dispatch_async(dispatch_get_main_queue()) { onStatus(false, false); onError(true) }
                return@dispatch_async
            }
            session.beginConfiguration()
            session.addInput(input)
            output.alwaysDiscardsLateVideoFrames = true
            output.videoSettings = mapOf("PixelFormatType" to
                NSNumber(unsignedInt = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange))
            output.setSampleBufferDelegate(delegate, queue)
            session.addOutput(output)
            session.commitConfiguration()
            device = selected
        }

        val presets = when (resolution) {
            "SD" -> listOf(AVCaptureSessionPreset640x480, AVCaptureSessionPreset1280x720)
            "FHD", "QHD" -> listOf(AVCaptureSessionPreset1920x1080, AVCaptureSessionPreset1280x720)
            "UHD" -> listOf(AVCaptureSessionPreset3840x2160, AVCaptureSessionPreset1920x1080)
            else -> listOf(AVCaptureSessionPreset1280x720, AVCaptureSessionPreset640x480)
        }
        presets.firstOrNull { session.canSetSessionPreset(it) }?.let { session.sessionPreset = it }
        lastFrameAt = Long.MIN_VALUE
        delivered = false
        sessionGeneration++
        decoder = CameraQrDecoder(IosZxingQrReader)
        applyRotation()
        session.startRunning()
        val hasTorch = device?.hasTorch == true
        val running = session.running
        dispatch_async(dispatch_get_main_queue()) {
            view.setNeedsLayout()
            onStatus(running, hasTorch)
            if (!running) onError(true)
        }
    }

    fun stop() = dispatch_async(queue) {
        sessionGeneration++
        device?.let { active ->
            if (active.hasTorch && active.lockForConfiguration(null)) {
                try { active.torchMode = AVCaptureTorchModeOff } finally { active.unlockForConfiguration() }
            }
        }
        if (session.running) session.stopRunning()
        dispatch_async(dispatch_get_main_queue()) { onStatus(false, false) }
    }

    private fun applyRotation() {
        output.connectionWithMediaType(AVMediaTypeVideo)?.let {
            if (it.isVideoOrientationSupported()) it.videoOrientation = rotation
        }
    }

    fun updateControls(zoom: Float, torch: Boolean) = dispatch_async(queue) {
        val active = device ?: return@dispatch_async
        if (!active.lockForConfiguration(null)) return@dispatch_async
        try {
            // CameraX reports linear zoom in field of view, so interpolate reciprocal zoom.
            val minZoom = active.minAvailableVideoZoomFactor
            val maxZoom = active.maxAvailableVideoZoomFactor
            val fraction = zoom.coerceIn(0f, 1f).toDouble()
            active.videoZoomFactor = 1.0 / ((1 - fraction) / minZoom + fraction / maxZoom)
            if (active.hasTorch) active.torchMode = if (torch) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        } finally {
            active.unlockForConfiguration()
        }
    }

    fun focus(x: Double, y: Double) {
        val point = preview.captureDevicePointOfInterestForPoint(CGPointMake(x, y))
        dispatch_async(queue) {
            val active = device ?: return@dispatch_async
            if (!active.lockForConfiguration(null)) return@dispatch_async
            try {
                if (active.isFocusPointOfInterestSupported() && active.isFocusModeSupported(AVCaptureFocusModeAutoFocus)) {
                    active.focusPointOfInterest = point
                    active.focusMode = AVCaptureFocusModeAutoFocus
                }
                if (active.isExposurePointOfInterestSupported() && active.isExposureModeSupported(AVCaptureExposureModeAutoExpose)) {
                    active.exposurePointOfInterest = point
                    active.exposureMode = AVCaptureExposureModeAutoExpose
                }
            } finally {
                active.unlockForConfiguration()
            }
        }
    }

    private fun read(frame: CVImageBufferRef, config: FrameControls, now: Long): String? {
        val guide = if (config.settings.cropEnabled) config.guide ?: return null else null
        val lockFlags: CVPixelBufferLockFlags = kCVPixelBufferLock_ReadOnly
        if (CVPixelBufferLockBaseAddress(frame, lockFlags) != 0) return null
        try {
            val width = CVPixelBufferGetWidthOfPlane(frame, 0uL).toInt()
            val height = CVPixelBufferGetHeightOfPlane(frame, 0uL).toInt()
            val crop = aspectFillCrop(width, height, config.size.width, config.size.height, guide) ?: return null
            val stride = CVPixelBufferGetBytesPerRowOfPlane(frame, 0uL).toInt()
            val base = CVPixelBufferGetBaseAddressOfPlane(frame, 0uL)?.reinterpret<ByteVar>() ?: return null
            val pixel: (Int, Int) -> Byte = { x, y -> base[(crop.top + y) * stride + crop.left + x] }
            val bytes = ByteArray(crop.width * crop.height) { index -> pixel(index % crop.width, index / crop.width) }
            val image = LumaImage(bytes, crop.width, crop.height)
            return decoder.read(config.settings, crop.width, crop.height, 0, now,
                readFrame = { IosZxingQrReader.read(image, it) }, pixel = pixel)
        } finally {
            CVPixelBufferUnlockBaseAddress(frame, lockFlags)
        }
    }
}
