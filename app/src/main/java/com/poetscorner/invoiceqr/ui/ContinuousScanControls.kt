package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    val typography = MaterialTheme.typography
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 14.dp)
            .testTag("continuous_scan_toggle").toggleable(state.enabled,
            role = Role.Switch, onValueChange = onEnabled), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.continuous_scan), Modifier.weight(1f).padding(end = 8.dp),
                style = typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (state.enabled) {
                        Text(stringResource(R.string.continuous_scan_count, numbers.format(state.added)),
                            Modifier.testTag("continuous_scan_count"), style = typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Switch(checked = state.enabled, onCheckedChange = null)
                }
            }
        }
    }
}
