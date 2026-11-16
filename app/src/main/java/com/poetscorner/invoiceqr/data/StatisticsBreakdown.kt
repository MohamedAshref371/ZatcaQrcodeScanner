package com.poetscorner.invoiceqr.data

import androidx.room.withTransaction
import java.math.BigDecimal
import com.poetscorner.invoiceqr.shared.InvoiceAmount
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsGroup
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsKey
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsResult
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsTotals
import com.poetscorner.invoiceqr.shared.StatisticsCategoryCalculation as SharedCalculation
import com.poetscorner.invoiceqr.shared.categoryBreakdown
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class StatisticsCategoryContribution(val category: Int, val calculation: StatisticsCategoryCalculation,
    val original: StatisticsTotals, val contribution: StatisticsTotals)

data class StatisticsGroupBreakdown(val original: StatisticsTotals,
    val net: StatisticsTotals, val categories: List<StatisticsCategoryContribution>)

data class StatisticsGroupBreakdownState(val selection: StatisticsSelection? = null,
    val result: StatisticsGroupBreakdown? = null, val loading: Boolean = false, val failed: Boolean = false)

internal fun StatisticsResult.toBreakdown(request: StatisticsRequest): StatisticsGroupBreakdown {
    require(request.categoryCalculationActive)
    val result = InvoiceStatisticsResult(summary.toShared(), groups.map {
        InvoiceStatisticsGroup(InvoiceStatisticsKey(it.key.text, it.key.periodStart), it.totals.toShared())
    }).categoryBreakdown(request.categoryCalculations.mapValues { SharedCalculation.valueOf(it.value.name) })
    return StatisticsGroupBreakdown(summary, result.net.toAndroid(), result.categories.map {
        StatisticsCategoryContribution(it.category, StatisticsCategoryCalculation.valueOf(it.calculation.name),
            it.original.toAndroid(), it.contribution.toAndroid())
    })
}

private fun StatisticsTotals.toShared() = InvoiceStatisticsTotals(count,
    requireNotNull(InvoiceAmount.parse(total.toPlainString())),
    requireNotNull(InvoiceAmount.parse(tax.toPlainString())), missingTotal, missingTax, ignoredCount)

private fun InvoiceStatisticsTotals.toAndroid() = StatisticsTotals(count, BigDecimal(total.toPlainString()),
    BigDecimal(tax.toPlainString()), missingTotal, missingTax, ignoredCount)

/** Reads only the selected group, using the same filters and amount parser as the overview. */
internal suspend fun ScanDatabase.statisticsBreakdown(selection: StatisticsSelection): StatisticsGroupBreakdown = withTransaction {
    require(selection.request.categoryCalculationActive)
    val originalRequest = selection.request.copy(grouping = StatisticsGrouping.CATEGORY)
    val accumulator = StatisticsAccumulator(originalRequest)
    var afterId = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = scans().statisticsRows(statisticsQuery(selection.request, afterId, selection.key))
        if (page.isEmpty()) break
        page.forEach(accumulator::add)
        afterId = page.last().id
    }
    accumulator.finish().toBreakdown(selection.request)
}
