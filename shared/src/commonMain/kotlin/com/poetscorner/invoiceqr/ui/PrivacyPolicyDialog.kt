package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.cancel
import com.poetscorner.invoiceqr.resources.privacy_link_error
import com.poetscorner.invoiceqr.resources.privacy_open_link
import com.poetscorner.invoiceqr.resources.privacy_open_message
import com.poetscorner.invoiceqr.resources.privacy_policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.poetscorner.invoiceqr.ui.text.stringResource

@Composable
internal fun PrivacyPolicyDialog(openPrivacyPolicy: () -> Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.privacy_policy)) },
        text = { Text(stringResource(Res.privacy_open_message)) },
        confirmButton = {
            TextButton(onClick = {
                if (openPrivacyPolicy()) onDismiss()
            }) { Text(stringResource(Res.privacy_open_link)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.cancel)) }
        }
    )
}
