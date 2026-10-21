package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.scanner.MlKitModuleState
import com.poetscorner.invoiceqr.scanner.MlKitModuleStatus

@Composable
internal fun ScanEnginePicker(selected: ScanEngine, module: MlKitModuleState,
    onSelect: (ScanEngine) -> Unit, onDownload: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scan_engine), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == ScanEngine.ZXING,
                role = Role.RadioButton, onClick = { onSelect(ScanEngine.ZXING) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == ScanEngine.ZXING, onClick = null)
                Text(stringResource(R.string.engine_zxing), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).heightIn(min = 56.dp).testTag("mlkit_option")
                    .selectable(selected == ScanEngine.ML_KIT, enabled = module.ready,
                        role = Role.RadioButton, onClick = { onSelect(ScanEngine.ML_KIT) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == ScanEngine.ML_KIT, onClick = null, enabled = module.ready)
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.engine_ml_kit), style = MaterialTheme.typography.bodyLarge)
                        val statusLabel = when (module.status) {
                            MlKitModuleStatus.CHECKING -> R.string.mlkit_checking
                            MlKitModuleStatus.NOT_INSTALLED -> R.string.mlkit_not_installed
                            MlKitModuleStatus.DOWNLOADING -> R.string.mlkit_downloading
                            MlKitModuleStatus.PAUSED -> R.string.mlkit_paused
                            MlKitModuleStatus.INSTALLING -> R.string.mlkit_installing
                            MlKitModuleStatus.READY -> null
                            MlKitModuleStatus.FAILED -> R.string.mlkit_failed
                            MlKitModuleStatus.UNAVAILABLE -> R.string.mlkit_services_required
                        }
                        statusLabel?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                            color = if (module.status == MlKitModuleStatus.FAILED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                when {
                    module.ready -> Unit
                    module.status == MlKitModuleStatus.CHECKING || module.inProgress ->
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> FilledTonalIconButton(onClick = onDownload, modifier = Modifier.testTag("mlkit_download")) {
                        AppIcon(R.drawable.ic_download, description = stringResource(
                            if (module.status == MlKitModuleStatus.FAILED || module.status == MlKitModuleStatus.UNAVAILABLE)
                                R.string.mlkit_retry else R.string.mlkit_download))
                    }
                }
            }
            if (module.inProgress) {
                val progress = module.progress
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!module.ready) Text(stringResource(R.string.mlkit_download_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
