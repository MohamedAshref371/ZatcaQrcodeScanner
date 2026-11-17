package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.resources.*
import org.jetbrains.compose.resources.StringResource

internal suspend fun iosInvoiceSummary(invoice: Invoice, locale: String,
    text: suspend (StringResource, Array<out Any>) -> String): String {
    suspend fun label(id: StringResource) = text(id, emptyArray())
    val summary = StringBuilder()
    summary.appendLine(label(Res.string.share_summary_title))
    val labels = mapOf(1 to Res.string.tag_seller, 2 to Res.string.tag_vat,
        3 to Res.string.tag_date, 4 to Res.string.tag_total, 5 to Res.string.tag_tax, 6 to Res.string.tag_hash)
    for ((tag, resource) in labels) {
        val value = invoice.value(tag)
        if (tag == 6 && value.isBlank()) continue
        if (tag == 3) {
            val timestamp = runCatching { ZatcaParser.parseTimestamp(value) }.getOrNull()
            if (timestamp == null) {
                summary.appendLine(label(resource) + ": " + value)
            } else {
                val (date, time) = formatInvoiceDateTime(timestamp.local, locale)
                val timeLabel = if (value.endsWith('Z')) Res.string.invoice_time_utc else Res.string.invoice_time_local
                summary.appendLine(label(resource) + ": " + date)
                summary.appendLine(label(timeLabel) + ": " + time)
            }
        } else {
            summary.appendLine(label(resource) + ": " + value.ifBlank { label(Res.string.missing) })
        }
    }
    val status = when {
        !invoice.passed -> Res.string.result_failed
        invoice.phase2 -> Res.string.share_phase_two_passed
        else -> Res.string.share_phase_one_passed
    }
    summary.appendLine(text(Res.string.share_check_result, arrayOf(label(status))))
    summary.appendLine(label(Res.string.verification_signature) + ": " +
        label(if (invoice.phase2) Res.string.verification_not_checked else Res.string.verification_not_present))
    summary.append(label(Res.string.verification_official) + ": " + label(Res.string.verification_not_checked))
    return summary.toString()
}
