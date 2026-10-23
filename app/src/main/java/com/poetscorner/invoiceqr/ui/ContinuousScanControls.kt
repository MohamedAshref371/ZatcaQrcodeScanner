package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import java.text.NumberFormat

enum class ContinuousScanStatus { SAVED, REVIEW, DUPLICATE, SAVE_FAILED }
data class ContinuousScanNotice(val result: ScanResult, val status: ContinuousScanStatus)
data class ContinuousScanState(val enabled: Boolean = false, val added: Int = 0,
    val notice: ContinuousScanNotice? = null)

@Composable
internal fun ContinuousScanControls(state: ContinuousScanState, onEnabled: (Boolean) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("continuous_scan_toggle").toggleable(state.enabled,
            role = Role.Switch, onValueChange = onEnabled), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.continuous_scan), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (state.enabled) {
                    Text(stringResource(R.string.continuous_scan_count, numbers.format(state.added)),
                        Modifier.testTag("continuous_scan_count"), style = MaterialTheme.typography.bodySmall)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Switch(checked = state.enabled, onCheckedChange = null)
            }
        }
    }
}
