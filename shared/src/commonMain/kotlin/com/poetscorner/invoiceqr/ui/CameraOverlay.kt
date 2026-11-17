package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.ScanWindow
import com.poetscorner.invoiceqr.scanner.scanGuideWindow
import com.poetscorner.invoiceqr.ui.text.UiText
import com.poetscorner.invoiceqr.ui.text.localized
import com.poetscorner.invoiceqr.ui.text.stringResource
import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.camera_allow
import com.poetscorner.invoiceqr.resources.camera_hint
import com.poetscorner.invoiceqr.resources.camera_hint_full_frame
import com.poetscorner.invoiceqr.resources.camera_permission_note
import com.poetscorner.invoiceqr.resources.ic_flash
import com.poetscorner.invoiceqr.resources.ic_scan
import com.poetscorner.invoiceqr.resources.permission_settings
import com.poetscorner.invoiceqr.resources.torch
import com.poetscorner.invoiceqr.resources.torch_off
import com.poetscorner.invoiceqr.resources.zoom

@Composable
fun CameraPermissionScreen(contentPadding: PaddingValues, topControls: @Composable () -> Unit,
    requestPermission: () -> Unit, openSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(12.dp)) {
        topControls()
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Res.drawable.ic_scan)
                    Text(stringResource(Res.string.camera_permission_note))
                    Button(onClick = requestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.camera_allow))
                    }
                    TextButton(onClick = openSettings) { Text(stringResource(Res.string.permission_settings)) }
                }
            }
        }
    }
}

@Composable
fun CameraOverlay(settings: ScannerSettings, zoom: Float, onZoom: (Float) -> Unit,
    onTorch: () -> Unit, contentPadding: PaddingValues, topControls: @Composable () -> Unit,
    focusIndicator: Offset?, error: UiText?, cameraReady: Boolean, hasTorch: Boolean,
    onGuideWindow: (ScanWindow) -> Unit, onPreviewSize: (IntSize) -> Unit,
    preview: @Composable (Modifier) -> Unit) {
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
        SideEffect { onGuideWindow(window) }
        Box(Modifier.fillMaxSize().testTag("camera_preview").clipToBounds().onSizeChanged(onPreviewSize)) {
            preview(Modifier.fillMaxSize())
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
                        Text(stringResource(if (settings.cropEnabled) Res.string.camera_hint else Res.string.camera_hint_full_frame),
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
                            Text(stringResource(Res.string.zoom), style = MaterialTheme.typography.labelMedium)
                            Slider(zoom, onValueChange = onZoom,
                                modifier = Modifier.weight(1f), enabled = cameraReady)
                            FilledTonalIconButton(onClick = onTorch, enabled = hasTorch) {
                                AppIcon(Res.drawable.ic_flash, description = stringResource(if (settings.torch) Res.string.torch_off else Res.string.torch))
                            }
                        }
                    }
                }
            }
        }
    }
}
