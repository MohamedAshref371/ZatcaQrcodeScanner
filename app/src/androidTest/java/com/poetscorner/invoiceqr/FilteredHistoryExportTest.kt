package com.poetscorner.invoiceqr

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class FilteredHistoryExportTest {
    private val names = mapOf(1 to "Seller", 2 to "VAT", 3 to "Date", 4 to "Total", 5 to "Tax", 6 to "Hash",
        7 to "Invoice number", 8 to "Category", 9 to "Note")
    private val day = LocalDate.of(2026, 10, 20).toEpochDay()
    private fun entry(id: Long, seller: String, invoiceDate: String, scanDate: String): HistoryEntry {
        val vat = if (seller == "Alpha") "31000000000000$id" else "32000000000000$id"
        val bytes = listOf(1 to seller, 2 to vat, 3 to invoiceDate, 4 to "115.00", 5 to "15.00")
            .fold(byteArrayOf()) { result, (tag, value) ->
                val data = value.toByteArray(Charsets.UTF_8)
                result + byteArrayOf(tag.toByte(), data.size.toByte()) + data
            }
        return HistoryEntry.from(ScanEntity(id, Base64.getEncoder().encodeToString(bytes), seller,
            Instant.parse(scanDate).toEpochMilli(), true, 1))
    }
    private val entries = listOf(
        entry(1, "Alpha", "2026-10-20T10:00:00+03:00", "2026-10-21T10:00:00Z"),
        entry(2, "Beta", "2026-10-20T10:00:00+03:00", "2026-10-20T10:00:00Z"),
        entry(3, "Alpha", "2026-10-19T10:00:00+03:00", "2026-10-20T10:00:00Z"))

    @Test fun bothExportFormatsRespectTheSearchAndSelectedDateSource() {
        listOf(HistoryDateSource.INVOICE to entries[0], HistoryDateSource.SCAN to entries[2]).forEach { (source, expected) ->
            listOf("alpha", "٣١").forEach { query ->
                val rows = HistoryFilter(query, day, day, source, "Asia/Riyadh").apply(entries).map { it.scan }
                val json = JSONArray(HistoryExporter.json(rows, names))
                assertEquals(1, json.length())
                assertEquals(expected.vat, json.getJSONObject(0).getString("VAT"))
                val csv = HistoryExporter.csv(rows, names)
                assertEquals(2, csv.split("\r\n").size)
                assertTrue(csv.contains(expected.vat))
                entries.filter { it != expected }.forEach { assertFalse(csv.contains(it.vat)) }
            }
        }
    }

    @Test fun noMatchesExportAnEmptyResultRatherThanAllScans() {
        val rows = HistoryFilter(query = "missing seller").apply(entries).map { it.scan }
        assertEquals(0, JSONArray(HistoryExporter.json(rows, names)).length())
        val csv = HistoryExporter.csv(rows, names)
        assertTrue(csv.endsWith("\r\n"))
        entries.forEach { assertFalse(csv.contains(it.vat)) }
    }

    @Test fun withoutFiltersBothFormatsExportAllScans() {
        val rows = HistoryFilter().apply(entries).map { it.scan }
        assertEquals(3, JSONArray(HistoryExporter.json(rows, names)).length())
        val csv = HistoryExporter.csv(rows, names)
        entries.forEach { assertTrue(csv.contains(it.vat)) }
    }
}
