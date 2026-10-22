package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class StatisticsExporterTest {
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
