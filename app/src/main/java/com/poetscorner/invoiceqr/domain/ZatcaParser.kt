package com.poetscorner.invoiceqr.domain

import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.shared.InvoiceCheckKind
import com.poetscorner.invoiceqr.shared.PayloadErrorKind
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser
import com.poetscorner.invoiceqr.ui.text.UiText
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.datetime.toJavaLocalDateTime

typealias Field = com.poetscorner.invoiceqr.shared.InvoiceField

data class Check(val label: UiText, val passed: Boolean, val phase: Int = 1)
enum class InvoiceType(val label: Int) { STANDARD(R.string.invoice_standard), SIMPLIFIED(R.string.invoice_simplified) }
data class Invoice(val raw: String, val fields: List<Field>, val checks: List<Check>, val error: UiText? = null) {
    val type get() = if (fields.any { it.tag == 9 }) InvoiceType.SIMPLIFIED else InvoiceType.STANDARD
    val phase2 get() = fields.any { it.tag in 6..9 }
    val passed get() = error == null && checks.filter { phase2 || it.phase == 1 }.all { it.passed }
    fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
}

object ZatcaParser {
    val names = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat, 3 to R.string.tag_date,
        4 to R.string.tag_total, 5 to R.string.tag_tax, 6 to R.string.tag_hash,
        7 to R.string.tag_signature, 8 to R.string.tag_key, 9 to R.string.tag_stamp)

    fun parse(raw: String): Invoice {
        val parsed = ZatcaInvoiceParser.parse(raw)
        val checks = parsed.checks.map { check ->
            val label = when (check.kind) {
                InvoiceCheckKind.STRUCTURE -> UiText(R.string.check_structure)
                InvoiceCheckKind.PRESENT -> UiText(R.string.check_present, UiText(names.getValue(requireNotNull(check.tag))))
                InvoiceCheckKind.STAMP -> UiText(R.string.check_stamp, UiText(names.getValue(requireNotNull(check.tag))))
                InvoiceCheckKind.VAT -> UiText(R.string.check_vat)
                InvoiceCheckKind.DATE -> UiText(R.string.check_date)
                InvoiceCheckKind.TOTAL -> UiText(R.string.check_total)
                InvoiceCheckKind.TAX -> UiText(R.string.check_tax)
                InvoiceCheckKind.TAX_TOTAL -> UiText(R.string.check_tax_total)
            }
            Check(label, check.passed, check.phase)
        }
        val error = parsed.error?.let {
            val id = when (it.kind) {
                PayloadErrorKind.PAYLOAD_LARGE -> R.string.error_payload_large
                PayloadErrorKind.BASE64 -> R.string.error_base64
                PayloadErrorKind.TLV_EMPTY -> R.string.error_tlv_empty
                PayloadErrorKind.TLV_HEADER -> R.string.error_tlv_header
                PayloadErrorKind.TLV_LENGTH -> R.string.error_tlv_length
                PayloadErrorKind.TAG_UNKNOWN -> R.string.error_tag_unknown
                PayloadErrorKind.TAG_DUPLICATE -> R.string.error_tag_duplicate
                PayloadErrorKind.TAG_BOUNDS -> R.string.error_tag_bounds
                PayloadErrorKind.UTF8 -> R.string.error_utf8
            }
            it.tag?.let { tag -> UiText(id, tag) } ?: UiText(id)
        }
        return Invoice(parsed.raw, parsed.fields, checks, error)
    }

    /** Interpret a zone-less tag 3 timestamp at Saudi UTC+03:00. */
    fun parseTimestamp(value: String): OffsetDateTime {
        val local = ZatcaInvoiceParser.parseTimestamp(value).toJavaLocalDateTime()
        val offset = if (value.endsWith('Z')) ZoneOffset.UTC else ZoneOffset.ofHours(3)
        return local.atOffset(offset)
    }

    fun decodeTlv(bytes: ByteArray): List<Field> = ZatcaInvoiceParser.decodeTlv(bytes)
}
