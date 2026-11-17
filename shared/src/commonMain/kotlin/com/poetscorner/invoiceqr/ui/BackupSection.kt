package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.backup_create
import com.poetscorner.invoiceqr.resources.backup_description
import com.poetscorner.invoiceqr.resources.backup_import
import com.poetscorner.invoiceqr.resources.backup_import_in_progress
import com.poetscorner.invoiceqr.resources.backup_in_progress
import com.poetscorner.invoiceqr.resources.export_in_progress
import com.poetscorner.invoiceqr.resources.settings_backup
import com.poetscorner.invoiceqr.resources.transfer_processed
import com.poetscorner.invoiceqr.resources.transfer_progress
import com.poetscorner.invoiceqr.resources.transfer_saving
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.poetscorner.invoiceqr.ui.text.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun BackupSection(enabled: Boolean, onBackup: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.settings_backup), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.backup_description), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onBackup, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.backup_create))
                }
                OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.backup_import))
                }
            }
        }
    }
}

@Composable
internal fun TransferProgress(state: DataTransferState) {
    val locale = uiLocaleTag()
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (state.saving) Res.string.transfer_saving else when (state.kind) {
                TransferKind.EXPORT -> Res.string.export_in_progress
                TransferKind.BACKUP -> Res.string.backup_in_progress
                TransferKind.IMPORT -> Res.string.backup_import_in_progress
            }), style = MaterialTheme.typography.bodyMedium)
            if (!state.saving) {
                state.total?.let { total ->
                    Text(stringResource(Res.string.transfer_progress, formatInteger(state.processed.toLong(), locale), formatInteger(total.toLong(), locale)),
                        style = MaterialTheme.typography.bodySmall)
                } ?: if (state.processed > 0) {
                    Text(stringResource(Res.string.transfer_processed, formatInteger(state.processed.toLong(), locale)),
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
