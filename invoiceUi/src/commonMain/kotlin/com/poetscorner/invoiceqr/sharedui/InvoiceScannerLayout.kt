package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceScanGuide
import com.poetscorner.invoiceqr.shared.invoiceScanGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScannerHeader(
    title: String, subtitle: String, chooseLabel: String, chooseEnabled: Boolean, onChoose: () -> Unit,
    continuousEnabled: Boolean, continuousLabel: String, countLabel: String,
    onContinuousChange: (Boolean) -> Unit, chooser: @Composable () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    FilledTonalIconButton(onClick = onChoose, enabled = chooseEnabled,
                        modifier = Modifier.testTag("invoice-scanner-choose")) {
                        Icon(ScannerImageIcon, chooseLabel, Modifier.size(24.dp))
                    }
                    chooser()
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 14.dp)
                    .testTag("continuous_scan_toggle").toggleable(continuousEnabled, role = Role.Switch,
                        onValueChange = onContinuousChange),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(continuousLabel, Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (continuousEnabled) {
                                Text(countLabel, Modifier.testTag("continuous_scan_count"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Switch(checked = continuousEnabled, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceScannerViewport(
    cropEnabled: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    topControls: @Composable () -> Unit,
    bottomControls: @Composable () -> Unit,
    previewOverlay: @Composable () -> Unit = {},
    preview: @Composable (InvoiceScanGuide, Int, Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        var headerHeight by remember { mutableIntStateOf(0) }
        var footerHeight by remember { mutableIntStateOf(0) }
        val margin = with(density) { 12.dp.roundToPx() }
        val left = with(density) { contentPadding.calculateLeftPadding(direction).roundToPx() }
        val right = with(density) { contentPadding.calculateRightPadding(direction).roundToPx() }
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val sideControls = if (landscape) ((width - left - right - margin * 2) * 0.32f).toInt() + margin else 0
        val guide = invoiceScanGuide(width, height,
            left + margin + if (direction == LayoutDirection.Ltr) sideControls else 0,
            with(density) { contentPadding.calculateTopPadding().roundToPx() } + margin + if (landscape) 0 else headerHeight + margin,
            right + margin + if (direction == LayoutDirection.Rtl) sideControls else 0,
            with(density) { contentPadding.calculateBottomPadding().roundToPx() } + margin + if (landscape) 0 else footerHeight + margin)
        Box(Modifier.fillMaxSize().testTag("camera_preview").clipToBounds()) {
            preview(guide, width, height)
            if (cropEnabled) Canvas(Modifier.fillMaxSize().testTag("camera_crop_guide")) {
                if (size.width < 1 || size.height < 1) return@Canvas
                val x = guide.left.toFloat()
                val y = guide.top.toFloat()
                val side = guide.size.toFloat()
                val radius = CornerRadius(20.dp.toPx())
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(x, y, x + side, y + side, radius))
                }
                drawPath(shade, Color.Black.copy(alpha = 0.5f))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x, y), Size(side, side),
                    radius, style = Stroke(1.dp.toPx()))
            }
            previewOverlay()
        }
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(12.dp)) {
            Column(Modifier.align(Alignment.TopStart).fillMaxHeight()
                .then(if (landscape) Modifier.fillMaxWidth(0.32f).verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.onSizeChanged { headerHeight = it.height }) { topControls() }
                if (!landscape) Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().onSizeChanged { footerHeight = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { bottomControls() }
            }
        }
    }
}

@Composable
fun InvoiceScannerFooter(hint: String, zoomLabel: String, torchLabel: String,
    zoom: Float, onZoom: (Float) -> Unit, onTorch: () -> Unit, zoomEnabled: Boolean, torchEnabled: Boolean,
    status: @Composable () -> Unit = {}) {
    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp)) {
        Text(hint, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = TextAlign.Center)
    }
    status()
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(zoomLabel, style = MaterialTheme.typography.labelMedium)
            Slider(zoom, onValueChange = onZoom, modifier = Modifier.weight(1f), enabled = zoomEnabled)
            FilledTonalIconButton(onClick = onTorch, enabled = torchEnabled) {
                Icon(ScannerFlashIcon, torchLabel, Modifier.size(24.dp))
            }
        }
    }
}
