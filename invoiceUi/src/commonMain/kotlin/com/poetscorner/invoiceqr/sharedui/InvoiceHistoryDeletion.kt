package com.poetscorner.invoiceqr.sharedui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal data class InvoiceHistoryDeletionSelection(
    val query: InvoiceHistoryQuery,
    val label: String,
    val preview: InvoiceHistoryDeletionPreview,
)

@Composable
internal fun InvoiceDeleteConfirmation(title: String, message: String, onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?) {
    val language = LocalInvoiceLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = if (onConfirm != null) ({
            TextButton(onClick = onDismiss) { Text(language.text("إلغاء", "Cancel")) }
        }) else null,
        confirmButton = {
            TextButton(onClick = onConfirm ?: onDismiss, modifier = Modifier.testTag("invoice-history-delete-confirm")) {
                if (onConfirm != null) {
                    Text(language.text("حذف", "Delete"), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(language.text("إغلاق", "Close"))
                }
            }
        },
    )
}
