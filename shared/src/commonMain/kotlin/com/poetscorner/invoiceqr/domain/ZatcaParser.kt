package com.poetscorner.invoiceqr.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.io.encoding.Base64


enum class InvoiceLabel {
    INVOICE_STANDARD,
    INVOICE_SIMPLIFIED,
    TAG_SELLER,
    TAG_VAT,
    TAG_DATE,
    TAG_TOTAL,
    TAG_TAX,
    TAG_HASH,
    TAG_SIGNATURE,
    TAG_KEY,
    TAG_STAMP,
    ERROR_PAYLOAD_LARGE,
    ERROR_BASE64,
    CHECK_STRUCTURE,
    CHECK_STAMP,
    CHECK_PRESENT,
    CHECK_VAT,
    CHECK_DATE,
    CHECK_TOTAL,
    CHECK_TAX,
    CHECK_TAX_TOTAL,
    ERROR_PAYLOAD,
    ERROR_TLV_EMPTY,
    ERROR_TLV_HEADER,
    ERROR_TAG_UNKNOWN,
    ERROR_TAG_DUPLICATE,
    ERROR_TLV_LENGTH,
    ERROR_TAG_BOUNDS,
    ERROR_UTF8,
}

data class InvoiceText(val label: InvoiceLabel, val args: List<Any> = emptyList()) {
    constructor(label: InvoiceLabel, vararg args: Any) : this(label, args.toList())
}

data class InvoiceTimestamp(val local: LocalDateTime, val offset: UtcOffset) {
    fun toInstant() = local.toInstant(offset)
}

data class Field(val tag: Int, val value: String, val size: Int)
data class Check(val label: InvoiceText, val passed: Boolean, val phase: Int = 1)
enum class InvoiceType(val label: InvoiceLabel) { STANDARD(InvoiceLabel.INVOICE_STANDARD), SIMPLIFIED(InvoiceLabel.INVOICE_SIMPLIFIED) }
data class Invoice(val raw: String, val fields: List<Field>, val checks: List<Check>, val error: InvoiceText? = null) {
    val type get() = if (fields.any { it.tag == 9 }) InvoiceType.SIMPLIFIED else InvoiceType.STANDARD
    val phase2 get() = fields.any { it.tag in 6..9 }
    val passed get() = error == null && checks.filter { phase2 || it.phase == 1 }.all { it.passed }
    fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
}

object ZatcaParser {
    val names = mapOf(1 to InvoiceLabel.TAG_SELLER, 2 to InvoiceLabel.TAG_VAT, 3 to InvoiceLabel.TAG_DATE,
        4 to InvoiceLabel.TAG_TOTAL, 5 to InvoiceLabel.TAG_TAX, 6 to InvoiceLabel.TAG_HASH,
        7 to InvoiceLabel.TAG_SIGNATURE, 8 to InvoiceLabel.TAG_KEY, 9 to InvoiceLabel.TAG_STAMP)

    private class InvalidPayload(val text: InvoiceText) : IllegalArgumentException()
    private fun ensure(valid: Boolean, id: InvoiceLabel, vararg args: Any) {
        if (!valid) throw InvalidPayload(InvoiceText(id, *args))
    }

    fun parse(raw: String): Invoice = try {
        ensure(raw.length <= 100_000, InvoiceLabel.ERROR_PAYLOAD_LARGE)
        val clean = raw.filterNot { it.isWhitespace() }
        ensure(clean.isNotEmpty() && clean.matches(Regex("[A-Za-z0-9+/]*={0,2}")), InvoiceLabel.ERROR_BASE64)
        val bytes = try { decodePayload(clean) }
            catch (_: IllegalArgumentException) { throw InvalidPayload(InvoiceText(InvoiceLabel.ERROR_BASE64)) }
        val fields = decodeTlv(bytes)
        val type = if (fields.any { it.tag == 9 }) InvoiceType.SIMPLIFIED else InvoiceType.STANDARD
        fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
        val total = ExactAmount.parseInvoiceAmount(value(4))
        val tax = ExactAmount.parseInvoiceAmount(value(5))
        val checks = mutableListOf<Check>()
        checks += Check(InvoiceText(InvoiceLabel.CHECK_STRUCTURE), true)
        // Keep each tag's presence and value checks together, regardless of the TLV input order.
        (1..9).forEach { tag ->
            if (tag == 9 && type != InvoiceType.SIMPLIFIED) return@forEach
            val presenceLabel = if (tag == 9) InvoiceLabel.CHECK_STAMP else InvoiceLabel.CHECK_PRESENT
            checks += Check(InvoiceText(presenceLabel, InvoiceText(names.getValue(tag))), value(tag).isNotBlank(), if (tag > 5) 2 else 1)
            when (tag) {
                2 -> checks += Check(InvoiceText(InvoiceLabel.CHECK_VAT), value(2).matches(Regex("3[0-9]{13}3")))
                3 -> checks += Check(InvoiceText(InvoiceLabel.CHECK_DATE), runCatching { parseTimestamp(value(3)) }.isSuccess)
                4 -> checks += Check(InvoiceText(InvoiceLabel.CHECK_TOTAL), total != null)
                5 -> {
                    checks += Check(InvoiceText(InvoiceLabel.CHECK_TAX), tax != null)
                    checks += Check(InvoiceText(InvoiceLabel.CHECK_TAX_TOTAL), total != null && tax != null && tax <= total)
                }
            }
        }
        Invoice(raw, fields, checks)
    } catch (e: IllegalArgumentException) {
        Invoice(raw, emptyList(), listOf(Check(InvoiceText(InvoiceLabel.CHECK_STRUCTURE), false)),
            (e as? InvalidPayload)?.text ?: InvoiceText(InvoiceLabel.ERROR_PAYLOAD))
    }

    private val payloadBase64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    private const val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun decodePayload(value: String): ByteArray {
        // The former Android decoder ignored unused bits in a final partial group.
        val end = value.indexOf('=').let { if (it == -1) value.length else it }
        val mask = when (end % 4) {
            2 -> 0x30
            3 -> 0x3c
            else -> return payloadBase64.decode(value)
        }
        val last = value[end - 1]
        val canonical = base64Alphabet[base64Alphabet.indexOf(last) and mask]
        return payloadBase64.decode(if (last == canonical) value else
            value.replaceRange(end - 1, end, canonical.toString()))
    }

    private val timestampPattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z?")

    /** Preserve Tag 3 text for display; interpret zone-less timestamps at Saudi UTC+03:00. */
    fun parseTimestamp(value: String): InvoiceTimestamp {
        require(timestampPattern.matches(value)) { "Invalid invoice timestamp format" }
        val utc = value.endsWith('Z')
        return InvoiceTimestamp(LocalDateTime.parse(value.removeSuffix("Z")),
            if (utc) UtcOffset.ZERO else UtcOffset(hours = 3))
    }

    /** Standard ZATCA one-byte lengths first; BER 0x81/0x82 is an interoperability fallback.
     * This preserves legitimate ZATCA lengths 129 and 130, which overlap BER markers. */
    fun decodeTlv(bytes: ByteArray): List<Field> {
        ensure(bytes.isNotEmpty(), InvoiceLabel.ERROR_TLV_EMPTY)
        return runCatching { decode(bytes, false) }.getOrElse { decode(bytes, true) }
    }

    private fun decode(bytes: ByteArray, extended: Boolean): List<Field> {
        var offset = 0
        val result = mutableListOf<Field>()
        fun next(): Int {
            ensure(offset < bytes.size, InvoiceLabel.ERROR_TLV_HEADER)
            return bytes[offset++].toInt() and 255
        }
        while (offset < bytes.size) {
            val tag = next()
            ensure(tag in 1..9, InvoiceLabel.ERROR_TAG_UNKNOWN, tag)
            ensure(result.none { it.tag == tag }, InvoiceLabel.ERROR_TAG_DUPLICATE, tag)
            val first = next()
            val length = if (extended) when (first) {
                0x81 -> next()
                0x82 -> (next() shl 8) or next()
                in 0x80..0xff -> throw InvalidPayload(InvoiceText(InvoiceLabel.ERROR_TLV_LENGTH))
                else -> first
            } else first
            ensure(length <= bytes.size - offset, InvoiceLabel.ERROR_TAG_BOUNDS, tag)
            val data = bytes.copyOfRange(offset, offset + length)
            offset += length
            // Tags 6 and 7 already contain Base64 text; only 8 and 9 need binary-to-text encoding.
            val value = if (tag <= 7) try {
                data.decodeToString(throwOnInvalidSequence = true)
            } catch (_: Exception) { throw InvalidPayload(InvoiceText(InvoiceLabel.ERROR_UTF8, tag)) }
            else Base64.Default.encode(data)
            result += Field(tag, value, length)
        }
        return result
    }
}
