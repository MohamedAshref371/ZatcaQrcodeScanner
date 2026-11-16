package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun InvoiceStatisticsCard(
    title: String, count: String, totalLabel: String, total: String, vatLabel: String, vat: String,
    notices: List<String>, modifier: Modifier = Modifier, summary: Boolean = false,
    summaryHint: String? = null, onOpen: (() -> Unit)? = null, actionLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(
        onClickLabel = actionLabel, role = Role.Button, onClick = onOpen) else Modifier),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (summary) colors.primary.copy(alpha = 0.2f) else colors.outlineVariant),
        colors = CardDefaults.cardColors(containerColor =
            if (summary) colors.primaryContainer else colors.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                Text(count, style = MaterialTheme.typography.bodyMedium,
                    color = if (summary) colors.onPrimaryContainer else colors.onSurface)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(totalLabel, style = MaterialTheme.typography.labelMedium)
                    Text(total, style = MaterialTheme.typography.headlineSmall,
                        color = if (summary) colors.onPrimaryContainer else colors.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vatLabel, style = MaterialTheme.typography.labelMedium)
                    Text(vat, style = MaterialTheme.typography.titleMedium)
                }
            }
            notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
            if (summaryHint != null) {
                HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = 0.15f))
                Text(summaryHint, style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
            }
        }
    }
}
