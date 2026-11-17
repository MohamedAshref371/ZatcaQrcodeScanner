package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.gallery_pick_multiple
import com.poetscorner.invoiceqr.resources.ic_image
import com.poetscorner.invoiceqr.resources.scan_local
import com.poetscorner.invoiceqr.resources.scanner_heading
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.poetscorner.invoiceqr.ui.text.stringResource
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.data.ScannerSettings

@Composable
internal fun ScannerHome(settings: ScannerSettings, state: ContinuousScanState, busy: Boolean,
    zoom: Float, onZoom: (Float) -> Unit,
    onGallery: () -> Unit, onContinuous: (Boolean) -> Unit,
    onScan: suspend (String) -> Boolean, platform: ScannerPlatform, contentPadding: PaddingValues = PaddingValues(), onTorch: () -> Unit) {
    val controls: @Composable () -> Unit = {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        SectionHeading(stringResource(Res.scanner_heading), stringResource(Res.scan_local))
                    }
                    FilledTonalIconButton(onClick = onGallery, enabled = !busy) {
                        AppIcon(Res.ic_image, description = stringResource(Res.gallery_pick_multiple))
                    }
                }
                ContinuousScanControls(state, onContinuous)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!busy) platform.Camera(settings, state.enabled, zoom, onZoom, onScan, onTorch, contentPadding, controls)
        else Box(Modifier.padding(contentPadding).padding(12.dp)) { controls() }
    }
}
