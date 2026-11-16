package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.InvoiceStatisticsKey
import com.poetscorner.invoiceqr.shared.StatisticsGrouping
import com.poetscorner.invoiceqr.shared.StatisticsCategoryCalculation
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class InvoiceStatisticsRowFilter(val seller: String, val vat: String, val excludedVat: String)

data class InvoiceStatisticsHistoryFilter(
    val rows: InvoiceStatisticsRowFilter,
    val sellerKey: String? = null,
    val vatKey: String? = null,
    val missingInvoiceDate: Boolean = false,
)

class InvoiceGroupBreakdownQuery internal constructor(
    val filter: InvoiceHistoryQuery,
    internal val calculations: Map<Int, StatisticsCategoryCalculation>,
    internal val language: InvoiceLanguage,
)

data class InvoiceGroupBreakdownDisplay(val count: Int, val amounts: InvoiceBreakdownAmounts, val sections: List<InvoiceBreakdownSection>)

fun interface InvoiceGroupBreakdownObserver {
    fun complete(result: InvoiceGroupBreakdownDisplay?)
}

internal data class InvoiceStatisticsDrillDown(val title: String, val filter: InvoiceHistoryFilter,
    val calculations: Map<Int, StatisticsCategoryCalculation>)

internal fun InvoiceStatisticsSettings.drillDown(title: String, key: InvoiceStatisticsKey): InvoiceStatisticsDrillDown {
    var filter = dates
    val rows = rowFilter
    val selection = when (grouping) {
        StatisticsGrouping.SELLER -> InvoiceStatisticsHistoryFilter(rows, sellerKey = key.text.orEmpty())
        StatisticsGrouping.VAT -> InvoiceStatisticsHistoryFilter(rows, vatKey = key.text.orEmpty())
        StatisticsGrouping.CATEGORY -> {
            filter = filter.copy(category = key.text?.toIntOrNull() ?: 0)
            InvoiceStatisticsHistoryFilter(rows)
        }
        else -> {
            val start = key.periodStart?.let(LocalDate::fromEpochDays)
            if (start != null) {
                val duration = when (grouping) {
                    StatisticsGrouping.DAY -> DatePeriod(days = 1)
                    StatisticsGrouping.MONTH -> DatePeriod(months = 1)
                    StatisticsGrouping.QUARTER -> DatePeriod(months = 3)
                    StatisticsGrouping.HALF_YEAR -> DatePeriod(months = 6)
                    else -> DatePeriod(years = 1)
                }
                val end = start + duration - DatePeriod(days = 1)
                filter = filter.copy(from = maxOf(start, filter.from ?: start), to = minOf(end, filter.to ?: end))
            }
            InvoiceStatisticsHistoryFilter(rows, missingInvoiceDate = start == null)
        }
    }
    return InvoiceStatisticsDrillDown(title, filter.copy(statistics = selection),
        if (grouping == StatisticsGrouping.CATEGORY) emptyMap() else calculations)
}
