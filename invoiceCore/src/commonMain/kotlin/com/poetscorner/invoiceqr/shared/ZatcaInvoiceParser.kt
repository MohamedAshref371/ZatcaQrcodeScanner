package com.poetscorner.invoiceqr.shared

import kotlin.io.encoding.Base64
import kotlin.text.CharacterCodingException
import kotlinx.datetime.LocalDateTime

data class InvoiceField(val tag: Int, val value: String, val size: Int)

enum class InvoiceCheckKind { STRUCTURE, PRESENT, STAMP, VAT, DATE, TOTAL, TAX, TAX_TOTAL }

data class InvoiceCheck(
    val kind: InvoiceCheckKind,
    val passed: Boolean,
    val phase: Int = 1,
    val tag: Int? = null,
)

enum class PayloadErrorKind {
    PAYLOAD_LARGE, BASE64, TLV_EMPTY, TLV_HEADER, TLV_LENGTH,
    TAG_UNKNOWN, TAG_DUPLICATE, TAG_BOUNDS, UTF8,
}

data class PayloadError(val kind: PayloadErrorKind, val tag: Int? = null)

data class ParsedInvoice(
    val raw: String,
    val fields: List<InvoiceField>,
    val checks: List<InvoiceCheck>,
    val error: PayloadError? = null,
) {
    val isSimplified get() = fields.any { it.tag == 9 }
    val phase2 get() = fields.any { it.tag in 6..9 }
    val passed get() = error == null && checks.filter { phase2 || it.phase == 1 }.all { it.passed }
    fun value(tag: Int): String = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
}

object ZatcaInvoiceParser {
    private const val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val optionalPadding = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
    private val base64Pattern = Regex("[A-Za-z0-9+/]*={0,2}")
    private val vatPattern = Regex("3[0-9]{13}3")
    private val timestampPattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z?")

    private class InvalidPayload(val error: PayloadError) : IllegalArgumentException()

    private fun ensure(valid: Boolean, kind: PayloadErrorKind, tag: Int? = null) {
        if (!valid) throw InvalidPayload(PayloadError(kind, tag))
    }

    fun parse(raw: String): ParsedInvoice = try {
        ensure(raw.length <= 100_000, PayloadErrorKind.PAYLOAD_LARGE)
        val fields = decodeTlv(decodePayload(raw))
        ParsedInvoice(raw, fields, checkFields(fields))
    } catch (e: InvalidPayload) {
        ParsedInvoice(raw, emptyList(), listOf(InvoiceCheck(InvoiceCheckKind.STRUCTURE, false)), e.error)
    }

    private fun decodePayload(raw: String): ByteArray {
        val clean = raw.filterNot { it.isWhitespace() }
        ensure(clean.isNotEmpty() && base64Pattern.matches(clean), PayloadErrorKind.BASE64)

        // Java's decoder accepted non-zero unused bits. Mask those bits for the stricter
        // Kotlin decoder while preserving the original payload used for display and deduplication.
        val symbols = clean.trimEnd('=').length
        val lastSymbol = symbols - 1
        val unusedBits = when (symbols % 4) {
            2 -> 4
            3 -> 2
            else -> 0
        }
        val compatible = if (unusedBits == 0) clean else {
            val index = base64Alphabet.indexOf(clean[lastSymbol])
            val masked = (index shr unusedBits) shl unusedBits
            clean.replaceRange(lastSymbol, symbols, base64Alphabet[masked].toString())
        }
        return try {
            optionalPadding.decode(compatible)
        } catch (_: IllegalArgumentException) {
            throw InvalidPayload(PayloadError(PayloadErrorKind.BASE64))
        }
    }

    private fun checkFields(fields: List<InvoiceField>): List<InvoiceCheck> = buildList {
        fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()

        val total = invoiceAmountCents(value(4))
        val tax = invoiceAmountCents(value(5))
        add(InvoiceCheck(InvoiceCheckKind.STRUCTURE, true))
        // Keep each tag's presence and value checks together regardless of TLV input order.
        for (tag in 1..9) {
            if (tag == 9 && fields.none { it.tag == 9 }) continue

            val presence = if (tag == 9) InvoiceCheckKind.STAMP else InvoiceCheckKind.PRESENT
            add(InvoiceCheck(presence, value(tag).isNotBlank(), if (tag > 5) 2 else 1, tag))
            when (tag) {
                2 -> add(InvoiceCheck(InvoiceCheckKind.VAT, vatPattern.matches(value(2))))
                3 -> add(InvoiceCheck(InvoiceCheckKind.DATE, isValidTimestamp(value(3))))
                4 -> add(InvoiceCheck(InvoiceCheckKind.TOTAL, total != null))
                5 -> {
                    add(InvoiceCheck(InvoiceCheckKind.TAX, tax != null))
                    val taxWithinTotal = tax != null && total != null &&
                        (tax.length < total.length || (tax.length == total.length && tax <= total))
                    add(InvoiceCheck(InvoiceCheckKind.TAX_TOTAL, taxWithinTotal))
                }
            }
        }
    }

    /** Returns the date and time as written in tag 3. A Z suffix indicates UTC. */
    @Throws(IllegalArgumentException::class)
    fun parseTimestamp(value: String): LocalDateTime {
        require(timestampPattern.matches(value)) { "Invalid invoice timestamp format" }
        return LocalDateTime.parse(value.removeSuffix("Z"))
    }

    private fun isValidTimestamp(value: String): Boolean = try {
        parseTimestamp(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /** Prefer one-byte lengths; ordinary lengths 129 and 130 overlap BER markers. */
    @Throws(IllegalArgumentException::class)
    fun decodeTlv(bytes: ByteArray): List<InvoiceField> {
        ensure(bytes.isNotEmpty(), PayloadErrorKind.TLV_EMPTY)
        return try {
            decode(bytes, extended = false)
        } catch (_: InvalidPayload) {
            decode(bytes, extended = true)
        }
    }

    private fun decode(bytes: ByteArray, extended: Boolean): List<InvoiceField> {
        var offset = 0
        val result = mutableListOf<InvoiceField>()
        fun next(): Int {
            ensure(offset < bytes.size, PayloadErrorKind.TLV_HEADER)
            return bytes[offset++].toInt() and 255
        }
        while (offset < bytes.size) {
            val tag = next()
            ensure(tag in 1..9, PayloadErrorKind.TAG_UNKNOWN, tag)
            ensure(result.none { it.tag == tag }, PayloadErrorKind.TAG_DUPLICATE, tag)
            val first = next()
            val length = if (extended) when (first) {
                0x81 -> next()
                0x82 -> (next() shl 8) or next()
                in 0x80..0xff -> throw InvalidPayload(PayloadError(PayloadErrorKind.TLV_LENGTH))
                else -> first
            } else first
            ensure(length <= bytes.size - offset, PayloadErrorKind.TAG_BOUNDS, tag)
            // Tags 6 and 7 already contain Base64 text. Tags 8 and 9 are binary.
            val value = if (tag <= 7) {
                try {
                    bytes.decodeToString(offset, offset + length, throwOnInvalidSequence = true)
                } catch (_: CharacterCodingException) {
                    throw InvalidPayload(PayloadError(PayloadErrorKind.UTF8, tag))
                }
            } else {
                Base64.encode(bytes, offset, offset + length)
            }
            result += InvoiceField(tag, value, length)
            offset += length
        }
        return result
    }
}
