package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.BoundQuery
import com.poetscorner.invoiceqr.data.ExportInvoice
import com.poetscorner.invoiceqr.data.HistoryCounts
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryExportSource
import com.poetscorner.invoiceqr.data.HistoryExports
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.HistoryQueries
import com.poetscorner.invoiceqr.data.HistorySearchFields
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedHistoryTest {
    @Test fun scanDateQueriesUseTheTwoMidnightsAcrossDaylightSaving() {
        val day = LocalDate(2024, 3, 10).toEpochDays()
        val filter = HistoryFilter("٣١", day, day, HistoryDateSource.SCAN, "America/New_York")
        val query = HistoryQueries(filter).counts()
        val start = 1_710_046_800_000L
        val end = 1_710_129_600_000L
        assertEquals(listOf("٣١", "31", start, end), query.arguments)
        val values = listOf(start - 1, start, end - 1, end)
        assertEquals(listOf(start, end - 1), filter.select(values) {
            HistorySearchFields("Seller", "3101", it, null, 0, "", "")
        })
    }

    @Test fun streamingJsonPreservesOrderingWhitespaceAndEscaping() = runTest {
        val row = ExportInvoice(1, 42, "متجر / \"النور\"", "3101", "2026-11-29T11:25:00Z",
            "1.00", "0.10", "", "", 0, "line\nnext")
        val names = (1..9).associateWith { "field$it" }
        val source = object : HistoryExportSource {
            private var first = true
            override suspend fun counts(query: BoundQuery) = HistoryCounts(1, 1)
            override suspend fun hasHash(query: BoundQuery) = false
            override suspend fun exportRows(query: BoundQuery) = if (first) {
                first = false
                listOf(row)
            } else emptyList()
        }
        val output = Buffer()
        HistoryExports.write(source, HistoryFilter(), output, csv = false, names)
        assertEquals(
            """[
  {
    "field7": "",
    "field1": "متجر \/ \"النور\"",
    "field2": "3101",
    "field3": "2026-11-29T11:25:00Z",
    "field4": "1.00",
    "field5": "0.10",
    "field8": "",
    "field9": "line\nnext"
  }
]""",
            output.readString(),
        )
    }
}
