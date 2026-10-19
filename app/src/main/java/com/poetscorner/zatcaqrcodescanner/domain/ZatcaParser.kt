package com.poetscorner.zatcaqrcodescanner.domain

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

data class Field(val tag: Int, val value: String, val size: Int)
data class Check(val label: String, val passed: Boolean, val phase: Int = 1)
enum class InvoiceType(val label: String) { STANDARD("فاتورة ضريبية"), SIMPLIFIED("فاتورة ضريبية مبسطة") }
data class Invoice(val raw: String, val fields: List<Field>, val checks: List<Check>, val error: String? = null) {
    val type get() = if (fields.any { it.tag == 9 }) InvoiceType.SIMPLIFIED else InvoiceType.STANDARD
    val phase2 get() = fields.any { it.tag in 6..9 }
    val passed get() = error == null && checks.filter { phase2 || it.phase == 1 }.all { it.passed }
    fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
}

object ZatcaParser {
    val names = mapOf(1 to "اسم البائع", 2 to "الرقم الضريبي", 3 to "التاريخ", 4 to "الإجمالي شامل الضريبة", 5 to "الضريبة", 6 to "Hash الفاتورة", 7 to "التوقيع", 8 to "الشهادة / المفتاح العام", 9 to "الختم / توقيع المفتاح العام")

    fun parse(raw: String): Invoice = try {
        require(raw.length <= 100_000) { "بيانات QR أكبر من الحد المسموح" }
        val clean = raw.filterNot { it.isWhitespace() }
        require(clean.isNotEmpty() && clean.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) { "رمز QR ليس Base64 صالحًا" }
        val bytes = Base64.getDecoder().decode(clean)
        val fields = decodeTlv(bytes)
        val type = if (fields.any { it.tag == 9 }) InvoiceType.SIMPLIFIED else InvoiceType.STANDARD
        fun value(tag: Int) = fields.firstOrNull { it.tag == tag }?.value.orEmpty()
        val checks = mutableListOf<Check>()
        checks += Check("بنية Base64 وTLV سليمة", true)
        (1..8).forEach { tag -> checks += Check("وجود ${names[tag]}", value(tag).isNotBlank(), if (tag > 5) 2 else 1) }
        if (type == InvoiceType.SIMPLIFIED) {
            checks += Check("وجود ${names[9]} (مطلوب للمبسطة)", value(9).isNotBlank(), 2)
        }
        checks += Check("الرقم الضريبي: 15 رقمًا ويبدأ بـ 3", value(2).matches(Regex("3[0-9]{14}")))
        checks += Check("التاريخ ISO 8601 (بدون منطقة زمنية = توقيت السعودية UTC+3)", runCatching {
            parseTimestamp(value(3))
        }.isSuccess)
        fun amount(tag: Int): BigDecimal? = value(tag).takeIf { it.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")) }?.toBigDecimalOrNull()
        checks += Check("الإجمالي رقم غير سالب (حتى منزلتين)", amount(4) != null)
        checks += Check("الضريبة رقم غير سالب (حتى منزلتين)", amount(5) != null)
        checks += Check("الضريبة لا تتجاوز الإجمالي", amount(4)?.let { total -> amount(5)?.let { it <= total } } == true)
        Invoice(raw, fields, checks)
    } catch (e: IllegalArgumentException) {
        Invoice(raw, emptyList(), listOf(Check("بنية Base64 وTLV سليمة", false)), e.message ?: "بيانات غير صالحة")
    }

    private val timestampPattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})?")

    /** Preserve Tag 3 text for display; interpret zone-less timestamps at Saudi UTC+03:00. */
    fun parseTimestamp(value: String): OffsetDateTime {
        val match = requireNotNull(timestampPattern.matchEntire(value)) { "صيغة تاريخ ISO 8601 غير صالحة" }
        return if (match.groupValues[1].isEmpty()) {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atOffset(ZoneOffset.ofHours(3))
        } else {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }
    }

    /** Standard ZATCA one-byte lengths first; BER 0x81/0x82 is an interoperability fallback.
     * This preserves legitimate ZATCA lengths 129 and 130, which overlap BER markers. */
    fun decodeTlv(bytes: ByteArray): List<Field> {
        require(bytes.isNotEmpty()) { "TLV فارغ" }
        return runCatching { decode(bytes, false) }.getOrElse { decode(bytes, true) }
    }

    private fun decode(bytes: ByteArray, extended: Boolean): List<Field> {
        var offset = 0
        val result = mutableListOf<Field>()
        fun next(): Int {
            require(offset < bytes.size) { "TLV مبتور: رأس أو طول غير مكتمل" }
            return bytes[offset++].toInt() and 255
        }
        while (offset < bytes.size) {
            val tag = next()
            require(tag in 1..9) { "Tag غير مدعوم: $tag" }
            require(result.none { it.tag == tag }) { "Tag مكرر: $tag" }
            val first = next()
            val length = if (extended) when (first) {
                0x81 -> next()
                0x82 -> (next() shl 8) or next()
                in 0x80..0xff -> throw IllegalArgumentException("ترميز طول TLV غير مدعوم")
                else -> first
            } else first
            require(length <= bytes.size - offset) { "طول Tag $tag يتجاوز البيانات المتاحة" }
            val data = bytes.copyOfRange(offset, offset + length)
            offset += length
            // Tags 6 and 7 already contain Base64 text; only 8 and 9 need binary-to-text encoding.
            val value = if (tag <= 7) try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
            } catch (_: Exception) { throw IllegalArgumentException("UTF-8 غير صالح في Tag $tag") }
            else Base64.getEncoder().encodeToString(data)
            result += Field(tag, value, length)
        }
        return result
    }
}
