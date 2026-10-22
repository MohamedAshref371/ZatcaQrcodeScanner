package com.poetscorner.invoiceqr.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.poetscorner.invoiceqr.BuildConfig
import com.poetscorner.invoiceqr.R

@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy)) },
        text = { Text(stringResource(R.string.privacy_open_message)) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
                    onDismiss()
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.privacy_link_error, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.privacy_link_error, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.privacy_open_link)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
