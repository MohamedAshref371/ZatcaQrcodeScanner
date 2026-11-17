package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.invoice_date_local
import com.poetscorner.invoiceqr.resources.invoice_date_time
import com.poetscorner.invoiceqr.resources.invoice_date_utc
import com.poetscorner.invoiceqr.resources.invoice_time
import com.poetscorner.invoiceqr.resources.tag_date
import com.poetscorner.invoiceqr.ui.text.stringResource

@Composable
internal fun invoiceTimestampTitle(value: String): String {
    val timestamp = runCatching { ZatcaParser.parseTimestamp(value) }.getOrNull()
    return stringResource(when {
        timestamp == null -> Res.string.invoice_date_time
        value.endsWith('Z') -> Res.string.invoice_date_utc
        else -> Res.string.invoice_date_local
    })
}

@Composable
internal fun invoiceTimestampFields(value: String): List<Pair<String, String>> {
    val label = stringResource(Res.string.tag_date)
    val timestamp = runCatching { ZatcaParser.parseTimestamp(value) }.getOrNull()
        ?: return listOf(label to value)
    // Display the invoice's wall-clock time, including when the device is in another time zone.
    val (date, time) = formatInvoiceDateTime(timestamp.local, uiLocaleTag())
    return listOf(label to date, stringResource(Res.string.invoice_time) to time)
}
