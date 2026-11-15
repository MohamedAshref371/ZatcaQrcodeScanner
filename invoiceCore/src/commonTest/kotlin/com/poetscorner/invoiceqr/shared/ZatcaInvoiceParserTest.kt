package com.poetscorner.invoiceqr.shared

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZatcaInvoiceParserTest {
    @Test
    fun acceptsLegacyBase64AndKeepsOriginalPayload() {
        for (raw in listOf("AQFB", " A Q F B \n", "AQA=", "AQA", "AQB=")) {
            val parsed = ZatcaInvoiceParser.parse(raw)
            assertNull(parsed.error, raw)
            assertEquals(raw, parsed.raw)
            assertEquals(1, parsed.fields.single().tag)
        }
        for (raw in listOf("A", "AQA==", "AQA===", "AQ=B", "====")) {
            assertEquals(PayloadErrorKind.BASE64, ZatcaInvoiceParser.parse(raw).error?.kind, raw)
        }
    }

    @Test
    fun preservesArabicAndBinaryPhaseTwoFields() {
        val hash = "aGFzaA=="
        val signature = "c2ln"
        val bytes = invoice() + field(6, hash) + field(7, signature) +
            byteArrayOf(8, 2, 0xff.toByte(), 0x80.toByte(), 9, 1, 0x7f)
        val parsed = parse(bytes)
        assertTrue(parsed.passed)
        assertTrue(parsed.phase2)
        assertTrue(parsed.isSimplified)
        assertEquals("متجر الاختبار", parsed.value(1))
        assertEquals(hash, parsed.value(6))
        assertEquals(signature, parsed.value(7))
        assertEquals("/4A=", parsed.value(8))
        assertEquals("fw==", parsed.value(9))
    }

    @Test
    fun preservesOneByteLengthPrecedenceAndBerFallback() {
        for (size in listOf(129, 130, 255)) {
            val fields = ZatcaInvoiceParser.decodeTlv(field(1, "A".repeat(size)))
            assertEquals(size, fields.single().size)
        }
        val extended = byteArrayOf(1, 0x82.toByte(), 1, 44) + "A".repeat(300).encodeToByteArray()
        assertEquals(300, ZatcaInvoiceParser.decodeTlv(extended).single().size)
        assertEquals(PayloadError(PayloadErrorKind.UTF8, 1), parse(byteArrayOf(1, 1, 0xff.toByte())).error)
        assertEquals(PayloadError(PayloadErrorKind.TAG_DUPLICATE, 1), parse(invoice() + field(1, "Again")).error)
    }

    @Test
    fun comparesAmountsExactlyAndKeepsReadableInvoicesWithFailedChecks() {
        val huge = "99999999999999999999999999999999"
        assertTrue(parse(invoice(total = "000012.30", tax = "12.3")).passed)
        assertTrue(parse(invoice(total = "$huge.01", tax = "$huge.01")).passed)
        val bad = parse(invoice(total = "$huge.01", tax = "$huge.02"))
        assertNull(bad.error)
        assertFalse(bad.passed)
        assertFalse(bad.checks.single { it.kind == InvoiceCheckKind.TAX_TOTAL }.passed)
        assertTrue(parse(invoice(total = "0", tax = "0.00")).passed)
    }

    @Test
    fun validatesTheExactCalendarDateAndTimestampSyntax() {
        for (value in listOf("2024-02-29T14:25:00", "2024-02-29T14:25:00Z")) {
            assertTrue(parse(invoice(timestamp = value)).passed, value)
        }
        for (value in listOf(
            "2023-02-29T14:25:00", "2024-02-30T14:25:00Z", "2024-11-29T24:00:00",
            "2024-11-29T14:25:60Z", "2024-11-29T14:25:00.123Z", "2024-11-29T14:25:00+03:00",
        )) {
            val parsed = parse(invoice(timestamp = value))
            assertNull(parsed.error)
            assertFalse(parsed.checks.single { it.kind == InvoiceCheckKind.DATE }.passed, value)
            assertEquals(value, parsed.value(3))
        }
    }

    private fun parse(bytes: ByteArray) = ZatcaInvoiceParser.parse(Base64.encode(bytes))

    private fun invoice(
        timestamp: String = "2024-02-29T14:25:00",
        total: String = "115.00",
        tax: String = "15.00",
    ) = field(1, "متجر الاختبار") + field(2, "310123456789013") +
        field(3, timestamp) + field(4, total) + field(5, tax)

    private fun field(tag: Int, value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
    }
}
