package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.domain.InvoiceType
import com.poetscorner.invoiceqr.data.ScanEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.time.ZoneOffset

class ZatcaParserTest {
    @Test fun scanRejectionDistinguishesMalformedPayloadsFromInvoiceCheckFailures() {
        listOf("https://example.org", "%%%", "", "aGVsbG8=", "AQVB").forEach { raw ->
            assertNotNull(raw, ZatcaParser.parse(raw).error)
        }
        // A readable invoice with failing business checks should still open its report.
        val invoice = parse(tlv(1, "Seller") + tlv(2, "invalid VAT"))
        assertNull(invoice.error)
        assertFalse(invoice.passed)
        assertNull(parse(valid()).error)
    }
    private fun tlv(tag: Int, text: String): ByteArray = text.toByteArray().let { byteArrayOf(tag.toByte(), it.size.toByte()) + it }
    private fun valid() = tlv(1, "متجر الاختبار") + tlv(2, "310123456789013") + tlv(3, "2026-10-19T12:30:00Z") + tlv(4, "115.00") + tlv(5, "15.00")
    private fun parse(bytes: ByteArray) = ZatcaParser.parse(Base64.getEncoder().encodeToString(bytes))
    @Test fun arabicUtf8AndValidPhaseOne() {
        val result = parse(valid())
        assertTrue(result.passed); assertFalse(result.phase2); assertEquals("متجر الاختبار", result.value(1))
    }
    @Test fun vatNumberMustAlsoEndWithThree() {
        val invoice = parse(tlv(1, "Seller") + tlv(2, "310123456789014") +
            tlv(3, "2026-10-19T12:30:00Z") + tlv(4, "115.00") + tlv(5, "15.00"))
        assertNull(invoice.error)
        assertFalse(invoice.checks.first { it.label.id == R.string.check_vat }.passed)
        assertFalse(invoice.passed)
        assertTrue(parse(valid()).checks.first { it.label.id == R.string.check_vat }.passed)
    }
    @Test fun phaseTwoRequiresTagsSixThroughEight() {
        assertFalse(parse(valid() + tlv(6, "hash")).passed)
        assertTrue(parse(valid() + (6..9).fold(byteArrayOf()) { b, t -> b + tlv(t, "binary") }).passed)
    }
    @Test fun invoiceTypeIsDetectedAutomaticallyFromStampPresence() {
        val bytes = valid() + (6..8).fold(byteArrayOf()) { b, t -> b + tlv(t, "binary") }
        val raw = Base64.getEncoder().encodeToString(bytes)
        assertTrue(ZatcaParser.parse(raw).passed)
        assertEquals(InvoiceType.STANDARD, ZatcaParser.parse(raw).type)
        val stamped = Base64.getEncoder().encodeToString(bytes + tlv(9, "stamp"))
        assertTrue(ZatcaParser.parse(stamped).passed)
        assertEquals(InvoiceType.SIMPLIFIED, ZatcaParser.parse(stamped).type)
    }
    @Test fun optionalStampDoesNotHideOtherFailuresOrAcceptMalformedTlv() {
        val missingKey = valid() + tlv(6, "hash") + tlv(7, "signature")
        assertFalse(parse(missingKey).passed)
        val bytes = valid() + (6..8).fold(byteArrayOf()) { b, t -> b + tlv(t, "binary") }
        assertFalse(parse(bytes + byteArrayOf(9, 5, 65)).passed)
        val emptyStamp = parse(bytes + byteArrayOf(9, 0))
        assertEquals(InvoiceType.SIMPLIFIED, emptyStamp.type)
        assertFalse(emptyStamp.passed)
        assertFalse(parse(bytes + tlv(9, "stamp") + tlv(9, "duplicate")).passed)
        val phaseOne = Base64.getEncoder().encodeToString(valid())
        assertTrue(ZatcaParser.parse(phaseOne).passed)
        assertEquals(InvoiceType.STANDARD, ZatcaParser.parse(phaseOne).type)
    }
    @Test fun historicalManualTypeDoesNotOverrideAutomaticClassification() {
        val bytes = valid() + (6..8).fold(byteArrayOf()) { b, t -> b + tlv(t, "binary") }
        val saved = ScanEntity(raw = Base64.getEncoder().encodeToString(bytes), seller = "متجر", passed = false,
            phase = 2, invoiceType = "SIMPLIFIED")
        val corrected = saved.revalidated()
        assertEquals("STANDARD", corrected.invoiceType)
        assertTrue(corrected.passed)
        val stamped = saved.copy(raw = Base64.getEncoder().encodeToString(bytes + tlv(9, "stamp")), invoiceType = "STANDARD")
        assertEquals("SIMPLIFIED", stamped.revalidated().invoiceType)
        assertTrue(stamped.revalidated().passed)
    }
    @Test fun hashAndSignatureKeepTheirOriginalBase64WithoutDoubleEncoding() {
        val hash = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 13).toByte() })
        val signature = Base64.getEncoder().encodeToString(ByteArray(64) { (255 - it).toByte() })
        val key = byteArrayOf(0x30, 0x02, 0xff.toByte(), 0x80.toByte())
        val stamp = byteArrayOf(0x30, 0x03, 0xfe.toByte(), 0x81.toByte())
        val invoice = parse(valid() + tlv(6, hash) + tlv(7, signature) +
            byteArrayOf(8, key.size.toByte()) + key + byteArrayOf(9, stamp.size.toByte()) + stamp)
        assertTrue(invoice.passed)
        assertEquals(hash, invoice.value(6))
        assertEquals(signature, invoice.value(7))
        assertEquals(32, Base64.getDecoder().decode(invoice.value(6)).size)
        assertEquals(64, Base64.getDecoder().decode(invoice.value(7)).size)
        assertEquals(Base64.getEncoder().encodeToString(key), invoice.value(8))
        assertEquals(Base64.getEncoder().encodeToString(stamp), invoice.value(9))
        // History and JSON export reparse this same saved raw payload.
        assertEquals(hash, ZatcaParser.parse(invoice.raw).value(6))
        assertEquals(signature, ZatcaParser.parse(invoice.raw).value(7))
    }
    @Test fun finalBadgeIncludesAllValueChecks() {
        val bad = tlv(1,"متجر") + tlv(2,"210123456789013") + tlv(3,"2026-02-30T12:00:00Z") + tlv(4,"10.00") + tlv(5,"15.00")
        assertFalse(parse(bad).passed)
        assertFalse(parse(valid().copyOf(valid().size - 1)).passed)
        assertFalse(parse(valid() + tlv(1,"مكرر")).passed)
        assertFalse(ZatcaParser.parse("https://example.org").passed)
    }
    @Test fun malformedCalendarDateFails() {
        val bytes = tlv(1,"متجر") + tlv(2,"310123456789013") + tlv(3,"2026-02-30T12:00:00Z") + tlv(4,"10") + tlv(5,"1")
        assertFalse(parse(bytes).checks.first { it.label.id == R.string.check_date }.passed)
    }
    @Test fun saudiLocalTimestampAndUtcRepresentTheSameInstant() {
        val local = ZatcaParser.parseTimestamp("2024-11-29T14:25:00")
        val utc = ZatcaParser.parseTimestamp("2024-11-29T11:25:00Z")
        val explicit = ZatcaParser.parseTimestamp("2024-11-29T14:25:00+03:00")
        assertEquals(ZoneOffset.ofHours(3), local.offset)
        assertEquals(ZoneOffset.UTC, utc.offset)
        assertEquals(utc.toInstant(), local.toInstant())
        assertEquals(explicit.toInstant(), local.toInstant())
        val text = "2024-11-29T14:25:00"
        val invoice = parse(tlv(1, "متجر") + tlv(2, "310123456789013") + tlv(3, text) + tlv(4, "115") + tlv(5, "15"))
        assertTrue(invoice.passed)
        assertEquals(text, invoice.value(3))
    }
    @Test fun compactOffsetsMatchColonOffsetsWithoutChangingRawInvoiceDate() {
        for ((compact, colon) in listOf("+0000" to "+00:00", "+03" to "+03:00", "+0300" to "+03:00", "+0530" to "+05:30", "-0400" to "-04:00")) {
            val value = "2026-10-22T15:45:08.123$compact"
            assertEquals(ZatcaParser.parseTimestamp("2026-10-22T15:45:08.123$colon"), ZatcaParser.parseTimestamp(value))
            val dateOnly = parse(tlv(3, value))
            assertEquals(value, dateOnly.value(3))
        }
        for (offset in listOf("+0360", "+1900", "+030", "+03000")) {
            assertTrue(runCatching { ZatcaParser.parseTimestamp("2026-10-22T15:45:08$offset") }.isFailure)
        }
    }
    @Test fun timestampsAcceptFractionsAndExplicitOffsets() {
        listOf("2024-11-29T14:25:00.123", "2024-11-29T11:25:00.123Z",
            "2024-11-29T14:25:00.123+03:00", "2024-11-29T06:25:00-05:00",
            "2024-02-29T14:25:00").forEach { assertTrue(it, runCatching { ZatcaParser.parseTimestamp(it) }.isSuccess) }
    }
    @Test fun zoneLessTimestampsStillRejectInvalidDatesAndTimes() {
        listOf("2024-11-31T14:25:00", "2023-02-29T14:25:00", "2024-11-29T24:25:00",
            "2024-11-29T14:60:00", "2024-11-29T14:25:60", "2024-11-29", "2024-11-29T14:25",
            "2024-11-29 14:25:00", "2024-11-29T14:25:00+25:00", "2024-11-29T14:25:00junk")
            .forEach { assertTrue(it, runCatching { ZatcaParser.parseTimestamp(it) }.isFailure) }
    }
    @Test fun berLengths81And82() {
        val data = "A".repeat(300).toByteArray()
        assertEquals(300, ZatcaParser.decodeTlv(byteArrayOf(1,0x82.toByte(),1,44) + data).single().size)
        assertEquals(3, ZatcaParser.decodeTlv(byteArrayOf(1,0x81.toByte(),3,65,66,67)).single().size)
    }
    @Test fun ordinaryLengths129And130RemainSupported() {
        listOf(129,130,255).forEach { size -> assertEquals(size, ZatcaParser.decodeTlv(tlv(1,"A".repeat(size))).single().size) }
    }
    @Test fun rejectsTruncationIndefiniteLengthsAndInvalidUtf8() {
        listOf(byteArrayOf(1), byteArrayOf(1,0x82.toByte(),1), byteArrayOf(1,0x81.toByte(),10,65),
            byteArrayOf(1,0x80.toByte(),65), byteArrayOf(1,1,0xff.toByte())).forEach { bytes -> assertFalse(parse(bytes).passed) }
    }
    @Test fun numericSyntaxRejectsNegativeNanExponentAndComma() {
        listOf("-1", "NaN", "1e2", "1,00", "1.001").forEach { value ->
            val bytes = tlv(1,"متجر") + tlv(2,"310123456789013") + tlv(3,"2026-10-19T12:30:00Z") + tlv(4,value) + tlv(5,"0")
            assertFalse(parse(bytes).passed)
        }
    }
}
