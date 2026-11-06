package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.HistoryEntry
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.ScanEntity
import com.poetscorner.invoiceqr.data.filterHistory
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

class HistorySearchTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val day = LocalDate.of(2026, 10, 20).toEpochDay()
    private fun entry(id: Long, seller: String, vat: String, time: String) = HistoryEntry(
        ScanEntity(id, "", seller, Instant.parse(time).toEpochMilli(), true, 1), vat)
    private val rows = listOf(
        entry(3, "متجر النور", "310123456789013", "2026-10-20T20:59:59.999Z"),
        entry(2, "North Market", "320123456789013", "2026-10-19T21:00:00Z"),
        entry(1, "متجر النور", "330123456789013", "2026-10-19T20:59:59.999Z"))

    @Test fun searchesSellerOrPartialVatAndKeepsNewestOrder() {
        assertEquals(listOf(3L, 1L), filterHistory(rows, " النور ", null, null, zone).map { it.scan.id })
        assertEquals(2L, filterHistory(rows, "north", null, null, zone).single().scan.id)
        assertEquals(1L, filterHistory(rows, "330123", null, null, zone).single().scan.id)
        assertTrue(filterHistory(rows, "unknown", null, null, zone).isEmpty())
        val numberedSeller = entry(4, "متجر ١٢٣", "", "2026-10-20T12:00:00Z")
        assertEquals(numberedSeller, filterHistory(listOf(numberedSeller), "١٢٣", null, null, zone).single())
    }

    @Test fun arabicAndPersianDigitsCanFindAsciiVatNumbers() {
        assertEquals(3L, filterHistory(rows, "٣١٠١٢٣", null, null, zone).single().scan.id)
        assertEquals(2L, filterHistory(rows, "۳۲۰۱۲۳", null, null, zone).single().scan.id)
    }

    @Test fun invoiceNumberAndNoteSearchStaySeparateFromSellerAndVatSearch() {
        val annotated = rows[1].copy(scan = rows[1].scan.copy(
            invoiceNumber = "INV-007", category = 1, note = "Client follow-up"))
        assertTrue(HistoryFilter(query = "INV-007").apply(listOf(annotated)).isEmpty())
        assertTrue(HistoryFilter(query = "follow-up").apply(listOf(annotated)).isEmpty())
        assertEquals(listOf(annotated), HistoryFilter(annotationQuery = "inv-007").apply(listOf(annotated)))
        assertEquals(listOf(annotated), HistoryFilter(annotationQuery = "FOLLOW-UP").apply(listOf(annotated)))
        assertEquals(listOf(annotated), HistoryFilter(query = "North", annotationQuery = "INV-007", category = 1)
            .apply(listOf(annotated)))
        assertTrue(HistoryFilter(query = "North", annotationQuery = "missing").apply(listOf(annotated)).isEmpty())
    }

    @Test fun dateRangeIncludesWholeLocalDaysAndCombinesWithSearch() {
        val after = entry(4, "Next day", "", "2026-10-20T21:00:00Z")
        assertEquals(listOf(3L, 2L), filterHistory(listOf(after) + rows, "", day, day, zone).map { it.scan.id })
        assertEquals(3L, filterHistory(rows, "النور", day, day, zone).single().scan.id)
        assertEquals(listOf(2L, 1L), filterHistory(rows, "", null, day - 1, ZoneId.of("UTC")).map { it.scan.id })
        assertEquals(rows, filterHistory(rows, "  ", null, null, zone))
    }

    @Test fun rangeUsesCalendarDaysAcrossDaylightSavingChanges() {
        val ny = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 11, 1).toEpochDay()
        val entries = listOf(
            entry(1, "Start", "", "2026-11-01T04:00:00Z"),
            entry(2, "Last hour", "", "2026-11-02T04:59:59.999Z"),
            entry(3, "Next day", "", "2026-11-02T05:00:00Z"))
        assertEquals(listOf(1L, 2L), filterHistory(entries, "", dstDay, dstDay, ny).map { it.scan.id })
    }

    @Test fun existingSavedPayloadProvidesVatWithoutDatabaseMigration() {
        val data = listOf(1 to "البائع", 2 to "310123456789013", 3 to "2020-01-01T10:00:00Z",
            4 to "115.00", 5 to "15.00").fold(byteArrayOf()) { acc, (tag, value) ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            acc + byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
        }
        val scan = ScanEntity(raw = Base64.getEncoder().encodeToString(data), seller = "البائع", passed = false, phase = 2)
        val indexed = HistoryEntry.from(scan)
        assertEquals("310123456789013", indexed.vat)
        assertEquals("115.00", indexed.total)
        assertEquals(1, indexed.scan.phase)
        assertTrue(indexed.scan.passed)
        assertEquals(scan.raw, indexed.scan.raw)
        assertEquals(LocalDate.of(2020, 1, 1).toEpochDay(), indexed.invoiceDay)
    }

    @Test fun invoiceAndScanDateFiltersAreIndependentAndIncludeBothEndpoints() {
        val entries = listOf(rows[0].copy(invoiceDay = day - 2), rows[1].copy(invoiceDay = day - 1),
            rows[2].copy(invoiceDay = day))
        assertEquals(listOf(3L, 2L), filterHistory(entries, "", day - 2, day - 1, zone,
            HistoryDateSource.INVOICE).map { it.scan.id })
        assertEquals(listOf(3L, 2L), filterHistory(entries, "", day, day, zone,
            HistoryDateSource.SCAN).map { it.scan.id })
        assertEquals(1L, filterHistory(entries, "النور", day, day, zone,
            HistoryDateSource.INVOICE).single().scan.id)
        assertEquals(listOf(2L, 1L), filterHistory(entries, "", day - 1, null, zone,
            HistoryDateSource.INVOICE).map { it.scan.id })
    }

    @Test fun missingInvoiceDatesAreExcludedOnlyWhenAnInvoiceDateRangeIsActive() {
        assertEquals(rows, filterHistory(rows, "", null, null, zone, HistoryDateSource.INVOICE))
        assertTrue(filterHistory(rows, "", day, null, zone, HistoryDateSource.INVOICE).isEmpty())
        assertEquals(2, filterHistory(rows, "", day, day, zone, HistoryDateSource.SCAN).size)
    }

    @Test fun invoiceDateKeepsItsWrittenCalendarDateRegardlessOfDeviceZone() {
        fun indexed(date: String): HistoryEntry {
            val bytes = date.toByteArray(Charsets.UTF_8)
            return HistoryEntry.from(ScanEntity(raw = Base64.getEncoder().encodeToString(
                byteArrayOf(3, bytes.size.toByte()) + bytes), seller = "", passed = false, phase = 1))
        }
        val invoice = indexed("2026-10-20T00:30:00")
        assertEquals(day, invoice.invoiceDay)
        assertEquals(day - 1, indexed("2026-10-19T21:30:00Z").invoiceDay)
        assertNull(indexed("2026-10-20T00:30:00+03:00").invoiceDay)
        assertNull(indexed("invalid date").invoiceDay)
        assertEquals(listOf(invoice), filterHistory(listOf(invoice), "", day, day,
            ZoneId.of("America/New_York"), HistoryDateSource.INVOICE))
    }
}
