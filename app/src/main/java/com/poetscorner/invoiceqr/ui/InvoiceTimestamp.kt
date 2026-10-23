package com.poetscorner.invoiceqr.ui

import android.content.Context
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.domain.ZatcaParser
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class InvoiceTimestampDisplay(val date: String, val time: String, val titleLabel: Int)

internal fun formatInvoiceTimestamp(value: String, locale: Locale): InvoiceTimestampDisplay? {
    val timestamp = runCatching { ZatcaParser.parseTimestamp(value) }.getOrNull() ?: return null
    // Format the invoice's own clock time; never convert to the device's time zone.
    val label = when {
        value.endsWith("Z") || value.endsWith("+00:00") -> R.string.invoice_date_gmt
        value.endsWith("+03:00") || value.endsWith("+0300") -> R.string.invoice_date_makkah
        else -> R.string.invoice_date_time
    }
    return InvoiceTimestampDisplay(
        timestamp.format(DateTimeFormatter.ofPattern("d MMMM uuuu", locale)),
        timestamp.format(DateTimeFormatter.ofPattern("h:mm:ss a", locale)),
        label
    )
}

internal fun invoiceTimestampTitle(context: Context, value: String): String = context.getString(
    formatInvoiceTimestamp(value, context.resources.configuration.locales[0])?.titleLabel
        ?: R.string.invoice_date_time
)

internal fun invoiceTimestampFields(context: Context, value: String, forSharing: Boolean = false): List<Pair<String, String>> {
    val formatted = formatInvoiceTimestamp(value, context.resources.configuration.locales[0])
        ?: return listOf(context.getString(R.string.tag_date) to value.ifBlank { context.getString(R.string.missing) })
    val timeLabel = if (forSharing) when (formatted.titleLabel) {
        R.string.invoice_date_gmt -> R.string.invoice_time_gmt
        R.string.invoice_date_makkah -> R.string.invoice_time_makkah
        else -> R.string.invoice_time
    } else R.string.invoice_time
    return listOf(
        context.getString(R.string.tag_date) to formatted.date,
        context.getString(timeLabel) to formatted.time
    )
}
