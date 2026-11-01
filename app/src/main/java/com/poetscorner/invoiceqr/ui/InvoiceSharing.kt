package com.poetscorner.invoiceqr.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.domain.Invoice

internal fun invoiceSummary(context: Context, invoice: Invoice): String = buildString {
    appendLine(context.getString(R.string.share_summary_title))
    val labels = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat,
        3 to R.string.export_invoice_date, 4 to R.string.tag_total, 5 to R.string.tag_tax,
        6 to R.string.tag_hash)
    for ((tag, label) in labels) {
        if (tag == 6 && invoice.value(tag).isBlank()) continue
        if (tag == 3) {
            invoiceTimestampFields(context, invoice.value(tag), forSharing = true).forEach { (title, value) ->
                appendLine("$title: $value")
            }
            continue
        }
        appendLine(context.getString(label) + ": " + invoice.value(tag).ifBlank { context.getString(R.string.missing) })
    }
    val status = when {
        !invoice.passed -> R.string.result_failed
        invoice.phase2 -> R.string.share_phase_two_passed
        else -> R.string.share_phase_one_passed
    }
    appendLine(context.getString(R.string.share_check_result, context.getString(status)))
    appendLine(context.getString(R.string.verification_signature) + ": " +
        context.getString(if (invoice.phase2) R.string.verification_not_checked else R.string.verification_not_present))
    append(context.getString(R.string.verification_official) + ": " + context.getString(R.string.verification_not_checked))
}

internal fun invoiceShareIntent(context: Context, invoice: Invoice): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_summary_title))
    putExtra(Intent.EXTRA_TEXT, invoiceSummary(context, invoice))
}

internal fun shareInvoice(context: Context, invoice: Invoice): Boolean = try {
    val chooser = Intent.createChooser(invoiceShareIntent(context, invoice), context.getString(R.string.share_invoice))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
    true
} catch (_: ActivityNotFoundException) {
    false
}

internal fun copyVat(context: Context, value: String) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.tag_vat), value))
    // Android 13+ already displays a system clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.vat_copied, Toast.LENGTH_SHORT).show()
    }
}
