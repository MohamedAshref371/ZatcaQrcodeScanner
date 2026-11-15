package com.poetscorner.invoiceqr.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceCsvTest {
    @Test
    fun historyKeepsOptionalHashAlignedAndEscapesSpreadsheetInput() {
        val headings = listOf("Seller", "VAT", "Date", "Total", "Tax", "Hash", "Number", "Category", "Note")
        val values = listOf("=SUM(1,2)", "300", "2026-11-15", "115.00", "15.00", "abc", "INV-1", "Return", "a\"b")
        val withoutHash = InvoiceHistoryCsv(headings, false)
        assertEquals("\uFEFF\"Number\",\"Seller\",\"VAT\",\"Date\",\"Total\",\"Tax\",\"Category\",\"Note\"\r\n",
            withoutHash.header())
        assertEquals("\"INV-1\",\"'=SUM(1,2)\",\"300\",\"2026-11-15\",\"115.00\",\"15.00\",\"Return\",\"a\"\"b\"",
            withoutHash.record(values))
        val withHash = InvoiceHistoryCsv(headings, true)
        assertEquals("\"INV-1\",\"'=SUM(1,2)\",\"300\",\"2026-11-15\",\"115.00\",\"15.00\",\"abc\",\"Return\",\"a\"\"b\"",
            withHash.record(values))
    }

    @Test
    fun statisticsOmitUnusedMissingColumnsAndBlankUnavailableAmounts() {
        val headings = listOf("Group", "Count", "Total", "Tax", "Missing total", "Missing tax")
        val csv = InvoiceStatisticsCsv(headings, includeMissingTotal = true, includeMissingTax = false)
        assertEquals("\uFEFF\"Group\",\"Count\",\"Total\",\"Tax\",\"Missing total\"\r\n", csv.header())
        assertEquals("\"'@seller\",\"2\",\"\",\"'-4.00\",\"1\"\r\n",
            csv.record("@seller", 2, "0.00", "-4.00", 1, 0, 1))
    }
}
