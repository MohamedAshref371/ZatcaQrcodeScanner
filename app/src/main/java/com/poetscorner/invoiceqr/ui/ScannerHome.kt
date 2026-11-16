package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.sharedui.InvoiceScannerHeader
import java.text.NumberFormat

@Composable
internal fun ScannerHome(settings: ScannerSettings, state: ContinuousScanState, busy: Boolean,
    zoom: Float, onZoom: (Float) -> Unit,
    onGallery: () -> Unit, onContinuous: (Boolean) -> Unit,
    onScan: suspend (String) -> Boolean, contentPadding: PaddingValues = PaddingValues(), onTorch: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val controls: @Composable () -> Unit = {
        InvoiceScannerHeader(
            title = stringResource(R.string.scanner_heading), subtitle = stringResource(R.string.scan_local),
            chooseLabel = stringResource(R.string.gallery_pick_multiple), chooseEnabled = !busy, onChoose = onGallery,
            continuousEnabled = state.enabled, continuousLabel = stringResource(R.string.continuous_scan),
            countLabel = stringResource(R.string.continuous_scan_count, numbers.format(state.added)),
            onContinuousChange = onContinuous,
        )
    }
    Box(Modifier.fillMaxSize()) {
        if (!busy) CameraScanner(settings, state.enabled, zoom, onZoom, onScan, onTorch, contentPadding, controls)
        else Box(Modifier.padding(contentPadding).padding(12.dp)) { controls() }
    }
}
