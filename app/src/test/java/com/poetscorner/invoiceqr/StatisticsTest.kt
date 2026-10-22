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
}
