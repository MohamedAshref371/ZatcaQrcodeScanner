package com.poetscorner.invoiceqr.ui

import android.content.Context
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class InvoiceTimestampDisplay(val date: String, val time: String, val titleLabel: Int)

internal fun formatInvoiceTimestamp(value: String, locale: Locale): InvoiceTimestampDisplay? {
    val timestamp = runCatching { ZatcaParser.parseTimestamp(value) }.getOrNull() ?: return null
    // Format the invoice's own clock time; never convert to the device's time zone.
    val label = if (value.endsWith('Z')) R.string.invoice_date_utc else R.string.invoice_date_local
    return InvoiceTimestampDisplay(
        timestamp.local.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", locale)),
        timestamp.local.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("h:mm:ss a", locale)),
        label
    )
}

internal fun invoiceTimestampTitle(context: Context, value: String): String = context.getString(
    formatInvoiceTimestamp(value, context.resources.configuration.locales[0])?.titleLabel
        ?: R.string.invoice_date_time
)

internal fun invoiceTimestampFields(context: Context, value: String, forSharing: Boolean = false): List<Pair<String, String>> {
    val formatted = formatInvoiceTimestamp(value, context.resources.configuration.locales[0])
        ?: return listOf(context.getString(R.string.tag_date) to value)
    val timeLabel = if (forSharing) when (formatted.titleLabel) {
        R.string.invoice_date_utc -> R.string.invoice_time_utc
        R.string.invoice_date_local -> R.string.invoice_time_local
        else -> R.string.invoice_time
    } else R.string.invoice_time
    return listOf(
        context.getString(R.string.tag_date) to formatted.date,
        context.getString(timeLabel) to formatted.time
    )
}
