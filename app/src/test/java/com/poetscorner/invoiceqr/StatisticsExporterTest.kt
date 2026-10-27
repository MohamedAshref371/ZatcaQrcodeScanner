package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class StatisticsExporterTest {
    @Test fun omitsEachUnusedMissingCountColumnFromHeadersGroupsAndSummary() = runBlocking {
        val headers = listOf("Group", "Count", "Available total", "Available tax", "Missing total", "Missing tax")
        for ((total, tax, expected) in listOf(
            Triple("10.00", "1.00", "\uFEFF\"Group\",\"Count\",\"Available total\",\"Available tax\"\r\n" +
                "\"Seller\",\"1\",\"10.00\",\"1.00\"\r\n\"Summary\",\"1\",\"10.00\",\"1.00\"\r\n"),
            Triple("invalid", "1.00", "\uFEFF\"Group\",\"Count\",\"Available total\",\"Available tax\",\"Missing total\"\r\n" +
                "\"Seller\",\"1\",\"\",\"1.00\",\"1\"\r\n\"Summary\",\"1\",\"\",\"1.00\",\"1\"\r\n"),
            Triple("10.00", "invalid", "\uFEFF\"Group\",\"Count\",\"Available total\",\"Available tax\",\"Missing tax\"\r\n" +
                "\"Seller\",\"1\",\"10.00\",\"\",\"1\"\r\n\"Summary\",\"1\",\"10.00\",\"\",\"1\"\r\n")
        )) {
            val accumulator = StatisticsAccumulator(StatisticsRequest())
            accumulator.add(StatisticsInvoice(1, "Seller", "3001", null, total, tax))
            val output = StringWriter()
            StatisticsExporter.write(output, accumulator.finish(), headers, "Summary") { requireNotNull(it.text) }
            assertEquals(expected, output.toString())
        }
    }

    @Test fun exportsSortedGroupsExactAmountsAndMissingCountsWithSafeCsvText() = runBlocking {
        val accumulator = StatisticsAccumulator(StatisticsRequest(sort = StatisticsSort.TOTAL, descending = true))
        accumulator.add(StatisticsInvoice(1, "=SUM(1,2)", "3001", null, "10.10", "invalid"))
        accumulator.add(StatisticsInvoice(2, "\"Quoted\"\nSeller", "3002", null, "invalid", "0.02"))
        val output = StringWriter()
        StatisticsExporter.write(output, accumulator.finish(), listOf("Group", "Count", "Available total", "Available tax", "Missing total", "Missing tax"), "Summary") {
            requireNotNull(it.text)
        }
        val csv = output.toString()
        assertTrue(csv.startsWith("\uFEFF\"Group\",\"Count\""))
        assertTrue(csv.contains("\"'=SUM(1,2)\",\"1\",\"10.10\",\"\",\"0\",\"1\"\r\n"))
        assertTrue(csv.contains("\"\"\"Quoted\"\"\nSeller\",\"1\",\"\",\"0.02\",\"1\",\"0\""))
        assertTrue(csv.endsWith("\"Summary\",\"2\",\"10.10\",\"0.02\",\"1\",\"1\"\r\n"))
        assertTrue(csv.indexOf("SUM") < csv.indexOf("Quoted"))
    }
}
