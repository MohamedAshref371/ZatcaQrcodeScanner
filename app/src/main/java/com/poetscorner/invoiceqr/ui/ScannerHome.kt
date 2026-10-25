package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.ScannerSettings

@Composable
internal fun ScannerHome(settings: ScannerSettings, state: ContinuousScanState, busy: Boolean,
    onGallery: () -> Unit, onContinuous: (Boolean) -> Unit,
    onScan: suspend (String) -> Boolean, contentPadding: PaddingValues = PaddingValues(), onTorch: () -> Unit) {
    val controls: @Composable () -> Unit = {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        SectionHeading(stringResource(R.string.scanner_heading), stringResource(R.string.scan_local))
                    }
                    FilledTonalIconButton(onClick = onGallery, enabled = !busy) {
                        AppIcon(R.drawable.ic_image, description = stringResource(R.string.gallery_pick))
                    }
                }
                ContinuousScanControls(state, onContinuous)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!busy) CameraScanner(settings, state.enabled, onScan, onTorch, contentPadding, controls)
        else Box(Modifier.padding(contentPadding).padding(12.dp)) { controls() }
    }
}
