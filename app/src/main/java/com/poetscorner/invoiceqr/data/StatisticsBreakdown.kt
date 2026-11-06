package com.poetscorner.invoiceqr.data

import androidx.room.withTransaction
import java.math.BigDecimal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class StatisticsCategoryContribution(val category: Int, val calculation: StatisticsCategoryCalculation,
    val original: StatisticsTotals, val contribution: StatisticsTotals)

data class StatisticsGroupBreakdown(val original: StatisticsTotals,
    val net: StatisticsTotals, val categories: List<StatisticsCategoryContribution>)

data class StatisticsGroupBreakdownState(val selection: StatisticsSelection? = null,
    val result: StatisticsGroupBreakdown? = null, val loading: Boolean = false, val failed: Boolean = false)

private fun StatisticsTotals.contribution(calculation: StatisticsCategoryCalculation): StatisticsTotals = when (calculation) {
    StatisticsCategoryCalculation.ADD -> this
    StatisticsCategoryCalculation.SUBTRACT -> copy(total = total.negate(), tax = tax.negate())
    StatisticsCategoryCalculation.IGNORE -> copy(total = BigDecimal.ZERO.setScale(2),
        tax = BigDecimal.ZERO.setScale(2), missingTotal = 0, missingTax = 0, ignoredCount = count)
}

private operator fun StatisticsTotals.plus(other: StatisticsTotals): StatisticsTotals = copy(
    count = count + other.count, total = total + other.total, tax = tax + other.tax,
    missingTotal = missingTotal + other.missingTotal, missingTax = missingTax + other.missingTax,
    ignoredCount = ignoredCount + other.ignoredCount)

internal fun StatisticsResult.toBreakdown(request: StatisticsRequest): StatisticsGroupBreakdown {
    require(request.categoryCalculationActive)
    val categories = groups.map { group ->
        val category = group.key.text?.toIntOrNull() ?: InvoiceCategories.NONE
        val calculation = request.calculationFor(category)
        StatisticsCategoryContribution(category, calculation, group.totals,
            group.totals.contribution(calculation))
    }.sortedWith(compareBy { InvoiceCategories.ids.indexOf(it.category).let { index ->
        if (index < 0) Int.MAX_VALUE else index
    } })
    return StatisticsGroupBreakdown(summary,
        categories.fold(StatisticsTotals()) { sum, category -> sum + category.contribution }, categories)
}

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
