package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun InvoiceHistoryRowCard(
    seller: String, vat: String?, annotation: String?, total: String,
    status: String?, passed: Boolean, checkedAt: String?, onOpen: () -> Unit,
    deleteDescription: String?, onDelete: (() -> Unit)?,
    enabled: Boolean = true, deleteEnabled: Boolean = enabled, modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(onClick = onOpen, enabled = enabled, modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(seller, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                vat?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = colors.onSurfaceVariant) }
                annotation?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                Text(total, style = MaterialTheme.typography.titleSmall)
                if (status != null) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tint = if (passed) colors.primary else colors.error
                    Icon(if (passed) CheckIcon else InfoIcon, null, Modifier.size(16.dp), tint = tint)
                    Text(status, style = MaterialTheme.typography.labelMedium, color = tint)
                }
                checkedAt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, enabled = deleteEnabled) {
                    Icon(InvoiceDeleteIcon, deleteDescription, Modifier.size(24.dp), tint = colors.error)
                }
            }
        }
    }
}
