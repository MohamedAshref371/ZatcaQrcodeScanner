package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal fun BackupSection(enabled: Boolean, onBackup: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_backup), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onBackup, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_create))
                }
                OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_import))
                }
            }
        }
    }
}

@Composable
internal fun TransferProgress(state: DataTransferState) {
    val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (state.saving) R.string.transfer_saving else when (state.kind) {
                TransferKind.EXPORT -> R.string.export_in_progress
                TransferKind.BACKUP -> R.string.backup_in_progress
                TransferKind.IMPORT -> R.string.backup_import_in_progress
            }), style = MaterialTheme.typography.bodyMedium)
            if (!state.saving) {
                state.total?.let { total ->
                    Text(stringResource(R.string.transfer_progress, number.format(state.processed), number.format(total)),
                        style = MaterialTheme.typography.bodySmall)
                } ?: if (state.processed > 0) {
                    Text(stringResource(R.string.transfer_processed, number.format(state.processed)),
                        style = MaterialTheme.typography.bodySmall)
                } else Unit
            }
            if (!state.saving && state.total != null && state.total > 0) {
                LinearProgressIndicator(progress = { (state.processed.toFloat() / state.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
