package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R

@Composable
internal fun SupporterPreviewBanner(onEnd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("supporter_preview_banner")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.supporter_preview_active), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onEnd, modifier = Modifier.testTag("supporter_preview_end")) {
                Text(stringResource(R.string.supporter_preview_end))
            }
        }
    }
}
