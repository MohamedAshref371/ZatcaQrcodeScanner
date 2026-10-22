package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class StatisticsTest {
    private fun row(id: Long = 1, seller: String = "Seller", vat: String = "3001", date: String? = "2026-01-01",
        total: String = "115.00", tax: String = "15.00") = StatisticsInvoice(id, seller, vat,
        date?.let { LocalDate.parse(it).toEpochDay() }, total, tax)
    private fun calculate(request: StatisticsRequest = StatisticsRequest(), vararg rows: StatisticsInvoice): StatisticsResult =
        StatisticsAccumulator(request).apply { rows.forEach(::add) }.finish()

    @Test fun sumsExactDecimalsAndCountsEveryInputIncludingRepeatedInvoices() {
        val invoice = row(total = "0.10", tax = "0.01")
        val result = calculate(StatisticsRequest(), invoice, invoice, row(total = "0.20", tax = "0.02"))
        assertEquals(3, result.summary.count)
        assertEquals(BigDecimal("0.40"), result.summary.total)
        assertEquals(BigDecimal("0.04"), result.summary.tax)
        assertEquals(result.summary, result.groups.single().totals)
    }

    @Test fun invalidAmountsAreExcludedIndependentlyWhileTheirInvoicesStillCount() {
        val result = calculate(StatisticsRequest(), row(total = "bad", tax = "15"), row(total = "100", tax = ""),
            row(total = "-1", tax = "1.234"), row(total = "1e2", tax = "NaN"))
        assertEquals(4, result.summary.count)
        assertEquals(BigDecimal("100.00"), result.summary.total)
        assertEquals(BigDecimal("15.00"), result.summary.tax)
        assertEquals(3, result.summary.missingTotal)
        assertEquals(3, result.summary.missingTax)
    }

    @Test fun sellerAndVatAreSeparateGroupingChoicesAndBlankKeysHaveTheirOwnGroup() {
        val rows = arrayOf(row(seller = "A", vat = "3001"), row(seller = "A", vat = "3002"), row(seller = "B", vat = "3001"),
            row(seller = " ", vat = ""))
        val seller = calculate(StatisticsRequest(), *rows).groups
        assertEquals(listOf("A", "B", null), seller.map { it.key.text })
        assertEquals(listOf(2, 1, 1), seller.map { it.totals.count })
        val vat = calculate(StatisticsRequest(grouping = StatisticsGrouping.VAT), *rows).groups
        assertEquals(listOf("3001", "3002", null), vat.map { it.key.text })
        assertEquals(listOf(2, 1, 1), vat.map { it.totals.count })
    }

    @Test fun calendarPeriodsRespectBoundariesAndNeverMixYears() {
        val dates = listOf("2025-12-31", "2026-01-01", "2026-03-31", "2026-04-01", "2026-06-30",
            "2026-07-01", "2026-12-31", "2027-01-01")
        val rows = dates.map { row(date = it) }.toTypedArray()
        val expected = mapOf(
            StatisticsGrouping.DAY to listOf(1, 1, 1, 1, 1, 1, 1, 1),
            StatisticsGrouping.MONTH to listOf(1, 1, 1, 1, 1, 1, 1, 1),
            StatisticsGrouping.QUARTER to listOf(1, 2, 2, 1, 1, 1),
            StatisticsGrouping.HALF_YEAR to listOf(1, 4, 2, 1),
            StatisticsGrouping.YEAR to listOf(1, 6, 1)
        )
        for ((grouping, counts) in expected) {
            val result = calculate(StatisticsRequest(grouping = grouping), *rows)
            assertEquals(grouping.name, counts, result.groups.map { it.totals.count })
            assertEquals(8, result.summary.count)
        }
        val quarter = calculate(StatisticsRequest(grouping = StatisticsGrouping.QUARTER), *rows).groups[2]
        assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), quarter.key.periodStart)
    }

    @Test fun unknownDatesRemainGroupedAndAllSortsWorkInBothDirections() {
        val unknown = calculate(StatisticsRequest(grouping = StatisticsGrouping.DAY, descending = true),
            row(date = null), row(date = "2026-01-01"), row(date = "2026-01-02"))
        assertEquals(3, unknown.summary.count)
        assertTrue(unknown.groups.last().key.unknown)
        val rows = arrayOf(row(seller = "A", total = "1", tax = "1"),
            row(seller = "B", total = "10", tax = "2"), row(seller = "B", total = "10", tax = "2"))
        for (sort in StatisticsSort.entries) for (descending in listOf(false, true)) {
            val result = calculate(StatisticsRequest(sort = sort, descending = descending), *rows)
            assertEquals("$sort $descending", if (descending) listOf("B", "A") else listOf("A", "B"),
                result.groups.map { it.key.text })
        }
    }

    @Test fun emptyInputHasZeroTotalsAndNoGroups() {
        val result = calculate()
        assertEquals(0, result.summary.count)
        assertTrue(result.groups.isEmpty())
        assertEquals(BigDecimal("0.00"), result.summary.total)
    }

    @Test fun dateShortcutsUseFullGregorianPeriodsAndPreserveOtherFilters() {
        val request = StatisticsRequest(grouping = StatisticsGrouping.VAT, sort = StatisticsSort.TAX,
            descending = true, seller = "Market", vat = "300")
        val examples = listOf(
            Triple(StatisticsPreset.LAST_MONTH, "2026-01-10", "2025-12-01" to "2025-12-31"),
            Triple(StatisticsPreset.THIS_MONTH, "2024-02-15", "2024-02-01" to "2024-02-29"),
            Triple(StatisticsPreset.THIS_QUARTER, "2026-05-10", "2026-04-01" to "2026-06-30"),
            Triple(StatisticsPreset.LAST_QUARTER, "2026-01-10", "2025-10-01" to "2025-12-31"),
            Triple(StatisticsPreset.LAST_QUARTER, "2026-04-01", "2026-01-01" to "2026-03-31"),
            Triple(StatisticsPreset.LAST_QUARTER, "2026-10-22", "2026-07-01" to "2026-09-30"),
            Triple(StatisticsPreset.THIS_YEAR, "2026-10-22", "2026-01-01" to "2026-12-31")
        )
        for ((preset, today, expected) in examples) {
            val result = request.withPeriod(preset, LocalDate.parse(today))
            assertEquals(LocalDate.parse(expected.first).toEpochDay(), result.fromDay)
            assertEquals(LocalDate.parse(expected.second).toEpochDay(), result.toDay)
            assertEquals(preset, result.datePreset)
            assertEquals(request, result.copy(fromDay = null, toDay = null, datePreset = null))
        }
    }

    @Test fun groupSelectionMatchesExactNamesAndIntersectsTheOriginalFilters() {
        val seller = StatisticsSelection(StatisticsRequest(seller = "Market", vat = "300"), StatisticsKey(text = "Market"))
        assertTrue(seller.matches("\u00a0Market\t", "3001", null))
        assertFalse(seller.matches("Market Branch", "3001", null))
        assertFalse(seller.matches("Market", "4001", null))
        val start = LocalDate.of(2026, 4, 1).toEpochDay()
        val quarter = StatisticsSelection(StatisticsRequest(grouping = StatisticsGrouping.QUARTER,
            fromDay = start + 10, toDay = start + 20), StatisticsKey(periodStart = start))
        assertTrue(quarter.matches("A", "3001", start + 10))
        assertTrue(quarter.matches("A", "3001", start + 20))
        assertFalse(quarter.matches("A", "3001", start + 9))
        assertFalse(quarter.matches("A", "3001", null))
        val unknown = StatisticsSelection(StatisticsRequest(grouping = StatisticsGrouping.DAY), StatisticsKey())
        assertTrue(unknown.matches("A", "3001", null))
        assertFalse(unknown.matches("A", "3001", start))
    }

    @Test fun excludedVatMatchesAnyContainingNumberAndAcceptsArabicInput() {
        val excluded = "300000000000001"
        val request = StatisticsRequest(excludedVat = "٣٠٠٠٠٠٠٠٠٠٠٠٠٠١")
        val selection = StatisticsSelection(request, StatisticsKey(text = "Market"))
        assertEquals(excluded, normalizeVatNumber(request.excludedVat))
        assertTrue(request.filtered)
        assertFalse(selection.matches("Market", excluded, null))
        assertFalse(selection.matches("Market", "٣٠٠٠٠٠٠٠٠٠٠٠٠٠١", null))
        assertEquals(excluded, normalizeVatNumber("۳۰۰۰۰۰۰۰۰۰۰۰۰۰۱"))
        assertFalse(selection.matches("Market", "\t$excluded ", null))
        assertTrue(selection.matches("Market", "300000000000002", null))
        assertTrue(selection.matches("Market", "", null))
        assertFalse(selection.matches("Market", excluded + "2", null))
        val partial = StatisticsSelection(StatisticsRequest(excludedVat = "٠٠١"), StatisticsKey(text = "Market"))
        assertFalse(partial.matches("Market", excluded, null))
        assertFalse(partial.matches("Market", "100123", null))
        assertTrue(partial.matches("Market", "300000000000002", null))
        assertTrue(partial.matches("Market", "", null))
        val empty = StatisticsSelection(StatisticsRequest(excludedVat = ""), StatisticsKey(text = "Market"))
        assertTrue(empty.matches("Market", excluded, null))
    }

    @Test fun relativeDatesMoveForwardWhileManualDatesStayFixed() {
        val original = StatisticsRequest(excludedVat = "300000000000001")
            .withPeriod(StatisticsPreset.THIS_MONTH, LocalDate.of(2026, 12, 15))
        val moved = original.resolvePeriod(LocalDate.of(2027, 1, 3))
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), moved.fromDay)
        assertEquals(LocalDate.of(2027, 1, 31).toEpochDay(), moved.toDay)
        assertEquals(original.excludedVat, moved.excludedVat)
        val manual = original.copy(datePreset = null)
        assertEquals(manual, manual.resolvePeriod(LocalDate.of(2027, 1, 3)))
    }

    @Test fun switchingVatModeUsesOneNumberAndClearsTheOppositeFilter() {
        val excluded = StatisticsRequest().withVatFilter(StatisticsVatMode.EXCLUDE, "٣٠٠٠٠٠٠٠٠٠٠٠٠٠١")
        assertEquals(StatisticsVatMode.EXCLUDE, excluded.vatMode)
        assertEquals("300000000000001", excluded.excludedVat)
        assertEquals("", excluded.vat)
        val included = excluded.withVatFilter(StatisticsVatMode.INCLUDE, "٣٠٠")
        assertEquals(StatisticsVatMode.INCLUDE, included.vatMode)
        assertEquals("300", included.vat)
        assertEquals("", included.excludedVat)
        val empty = included.withVatFilter(StatisticsVatMode.EXCLUDE, "")
        assertEquals(StatisticsVatMode.EXCLUDE, empty.vatMode)
        assertFalse(empty.filtered)
    }
}
