package com.poetscorner.invoiceqr.domain

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.ui.text.UiText

data class Field(val tag: Int, val value: String, val size: Int)
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

    private class InvalidPayload(val text: UiText) : IllegalArgumentException()
    private fun ensure(valid: Boolean, id: Int, vararg args: Any) {
        if (!valid) throw InvalidPayload(UiText(id, *args))
    }

    fun parse(raw: String): Invoice = try {
        ensure(raw.length <= 100_000, R.string.error_payload_large)
        val clean = raw.filterNot { it.isWhitespace() }
        ensure(clean.isNotEmpty() && clean.matches(Regex("[A-Za-z0-9+/]*={0,2}")), R.string.error_base64)
        val bytes = try { Base64.getDecoder().decode(clean) }
            catch (_: IllegalArgumentException) { throw InvalidPayload(UiText(R.string.error_base64)) }
        val fields = decodeTlv(bytes)
        val type = if (fields.any { it.tag == 9 }) InvoiceType.SIMPLIFIED else InvoiceType.STANDARD
        fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
        fun amount(tag: Int): BigDecimal? = value(tag).takeIf { it.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")) }?.toBigDecimalOrNull()
        val checks = mutableListOf<Check>()
        checks += Check(UiText(R.string.check_structure), true)
        // Keep each tag's presence and value checks together, regardless of the TLV input order.
        (1..9).forEach { tag ->
            if (tag == 9 && type != InvoiceType.SIMPLIFIED) return@forEach
            val presenceLabel = if (tag == 9) R.string.check_stamp else R.string.check_present
            checks += Check(UiText(presenceLabel, UiText(names.getValue(tag))), value(tag).isNotBlank(), if (tag > 5) 2 else 1)
            when (tag) {
                2 -> checks += Check(UiText(R.string.check_vat), value(2).matches(Regex("3[0-9]{13}3")))
                3 -> checks += Check(UiText(R.string.check_date), runCatching { parseTimestamp(value(3)) }.isSuccess)
                4 -> checks += Check(UiText(R.string.check_total), amount(4) != null)
                5 -> {
                    checks += Check(UiText(R.string.check_tax), amount(5) != null)
                    checks += Check(UiText(R.string.check_tax_total), amount(4)?.let { total -> amount(5)?.let { it <= total } } == true)
                }
            }
        }
        Invoice(raw, fields, checks)
    } catch (e: IllegalArgumentException) {
        Invoice(raw, emptyList(), listOf(Check(UiText(R.string.check_structure), false)),
            (e as? InvalidPayload)?.text ?: UiText(R.string.error_payload))
    }

    private val timestampPattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(Z|[+-]\\d{2}:?\\d{2}|\\+03)?")

    /** Preserve Tag 3 text for display; interpret zone-less timestamps at Saudi UTC+03:00. */
    fun parseTimestamp(value: String): OffsetDateTime {
        val match = requireNotNull(timestampPattern.matchEntire(value)) { "Invalid ISO 8601 timestamp" }
        return if (match.groupValues[1].isEmpty()) {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atOffset(ZoneOffset.ofHours(3))
        } else {
            val offset = match.groupValues[1]
            val normalized = when (offset.length) {
                3 -> value + ":00"
                5 -> value.dropLast(5) + offset.take(3) + ":" + offset.takeLast(2)
                else -> value
            }
            OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }
    }

    /** Standard ZATCA one-byte lengths first; BER 0x81/0x82 is an interoperability fallback.
     * This preserves legitimate ZATCA lengths 129 and 130, which overlap BER markers. */
    fun decodeTlv(bytes: ByteArray): List<Field> {
        ensure(bytes.isNotEmpty(), R.string.error_tlv_empty)
        return runCatching { decode(bytes, false) }.getOrElse { decode(bytes, true) }
    }

    private fun decode(bytes: ByteArray, extended: Boolean): List<Field> {
        var offset = 0
        val result = mutableListOf<Field>()
        fun next(): Int {
            ensure(offset < bytes.size, R.string.error_tlv_header)
            return bytes[offset++].toInt() and 255
        }
        while (offset < bytes.size) {
            val tag = next()
            ensure(tag in 1..9, R.string.error_tag_unknown, tag)
            ensure(result.none { it.tag == tag }, R.string.error_tag_duplicate, tag)
            val first = next()
            val length = if (extended) when (first) {
                0x81 -> next()
                0x82 -> (next() shl 8) or next()
                in 0x80..0xff -> throw InvalidPayload(UiText(R.string.error_tlv_length))
                else -> first
            } else first
            ensure(length <= bytes.size - offset, R.string.error_tag_bounds, tag)
            val data = bytes.copyOfRange(offset, offset + length)
            offset += length
            // Tags 6 and 7 already contain Base64 text; only 8 and 9 need binary-to-text encoding.
            val value = if (tag <= 7) try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
            } catch (_: Exception) { throw InvalidPayload(UiText(R.string.error_utf8, tag)) }
            else Base64.getEncoder().encodeToString(data)
            result += Field(tag, value, length)
        }
        return result
    }
}
