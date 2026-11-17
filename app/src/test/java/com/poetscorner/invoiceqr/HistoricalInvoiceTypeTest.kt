package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.domain.InvoiceType
import com.poetscorner.invoiceqr.data.ScanEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class HistoricalInvoiceTypeTest {
    private fun tlv(tag: Int, text: String): ByteArray = text.toByteArray().let { byteArrayOf(tag.toByte(), it.size.toByte()) + it }
    private fun valid() = tlv(1, "متجر الاختبار") + tlv(2, "310123456789013") + tlv(3, "2026-10-19T12:30:00Z") + tlv(4, "115.00") + tlv(5, "15.00")
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
}
