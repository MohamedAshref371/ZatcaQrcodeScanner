package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.HistoryExporter
import com.poetscorner.invoiceqr.data.ScanEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class HistoryExporterTest {
    private val names = mapOf(1 to "Seller name", 2 to "VAT number", 3 to "Invoice date",
        4 to "Total including VAT", 5 to "VAT amount", 6 to "Invoice hash")

    private fun row(name: String = "Customer", hash: String? = null): ScanEntity {
        val values = listOf(1 to name, 2 to "310123456789013", 3 to "2020-01-15T10:30:00Z",
            4 to "115.00", 5 to "15.00") + listOfNotNull(hash?.let { 6 to it }) +
            listOf(7 to "signature", 8 to "public key", 9 to "stamp")
        val bytes = values.fold(byteArrayOf()) { result, (tag, value) ->
            val data = value.toByteArray(Charsets.UTF_8)
            result + byteArrayOf(tag.toByte(), data.size.toByte()) + data
        }
        return ScanEntity(id = 99, raw = Base64.getEncoder().encodeToString(bytes),
            seller = "Cached name", scannedAt = 123, passed = false, phase = 2)
    }

    @Test fun csvExportsOnlyRequestedInvoiceFieldsAndOmitsAnUnusedHashColumn() {
        val csv = HistoryExporter.csv(listOf(row()), names)
        assertEquals("\uFEFF\"Seller name\",\"VAT number\",\"Invoice date\",\"Total including VAT\",\"VAT amount\"\r\n" +
            "\"Customer\",\"310123456789013\",\"2020-01-15T10:30:00Z\",\"115.00\",\"15.00\"", csv)
    }

    @Test fun mixedInvoicesKeepTheHashColumnAlignedAndUseLocalizedHeaders() {
        val localized = names + (1 to "اسم البائع")
        val lines = HistoryExporter.csv(listOf(row(), row(hash = "abc+/=")), localized).split("\r\n")
        assertTrue(lines[0].startsWith("\uFEFF\"اسم البائع\""))
        assertTrue(lines[0].endsWith("\"Invoice hash\""))
        assertTrue(lines[1].endsWith(",\"\""))
        assertTrue(lines[2].endsWith(",\"abc+/=\""))
    }

    @Test fun spreadsheetFormulaProtectionAndQuotedNamesArePreserved() {
        val csv = HistoryExporter.csv(listOf(row(name = "=SUM(1,2)\"")), names)
        assertTrue(csv.contains("\"'=SUM(1,2)\"\"\""))
    }
}
