package com.poetscorner.invoiceqr.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class InvoiceStatisticsTest {
    @Test
    fun returnSubtractsZeroTaxAndIgnoredRowsDoNotCountAsMissing() {
        val statistics = InvoiceStatisticsAccumulator(InvoiceStatisticsOptions(
            grouping = StatisticsGrouping.MONTH,
            categoryCalculations = mapOf(3 to StatisticsCategoryCalculation.SUBTRACT, 6 to StatisticsCategoryCalculation.IGNORE),
        ))
        val day = LocalDate(2026, 11, 15).toEpochDays()
        statistics.add("Seller", "300000000000003", day, "100.01", "15.02", 1)
        statistics.add("Seller", "300000000000003", day, "120.5", "0", 3)
        statistics.add("Seller", "", day, "bad", "", 6)
        statistics.add("Other", "", null, "invalid", "0.00", 0)
        val result = statistics.finish()
        assertEquals(4, result.summary.count)
        assertEquals("-20.49", result.summary.total.toPlainString())
        assertEquals("15.02", result.summary.tax.toPlainString())
        assertEquals(1, result.summary.missingTotal)
        assertEquals(0, result.summary.missingTax)
        assertEquals(1, result.summary.ignoredCount)
        assertEquals(LocalDate(2026, 11, 1).toEpochDays(), result.groups[0].key.periodStart)
        assertEquals(true, result.groups[1].key.unknown)
    }

    @Test
    fun categoryOverviewKeepsOriginalAmountsAndAddsBeyondFixedWidth() {
        val statistics = InvoiceStatisticsAccumulator(InvoiceStatisticsOptions(
            grouping = StatisticsGrouping.CATEGORY,
            categoryCalculations = mapOf(3 to StatisticsCategoryCalculation.SUBTRACT),
        ))
        statistics.add("", "", null, "9".repeat(45) + ".99", "0", 3)
        statistics.add("", "", null, "0.01", "0.00", 3)
        val result = statistics.finish()
        assertEquals("1" + "0".repeat(45) + ".00", result.summary.total.toPlainString())
        assertEquals("0.00", result.summary.tax.toPlainString())
        assertEquals("3", result.groups.single().key.text)
    }
}
