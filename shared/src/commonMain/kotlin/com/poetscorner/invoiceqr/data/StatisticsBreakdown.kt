package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ExactAmount

data class StatisticsCategoryContribution(val category: Int, val calculation: StatisticsCategoryCalculation,
    val original: StatisticsTotals, val contribution: StatisticsTotals)

data class StatisticsGroupBreakdown(val original: StatisticsTotals,
    val net: StatisticsTotals, val categories: List<StatisticsCategoryContribution>)

data class StatisticsGroupBreakdownState(val selection: StatisticsSelection? = null,
    val result: StatisticsGroupBreakdown? = null, val loading: Boolean = false, val failed: Boolean = false)

private fun StatisticsTotals.contribution(calculation: StatisticsCategoryCalculation): StatisticsTotals = when (calculation) {
    StatisticsCategoryCalculation.ADD -> this
    StatisticsCategoryCalculation.SUBTRACT -> copy(total = -total, tax = -tax)
    StatisticsCategoryCalculation.IGNORE -> copy(total = ExactAmount.ZERO,
        tax = ExactAmount.ZERO, missingTotal = 0, missingTax = 0, ignoredCount = count)
}

private operator fun StatisticsTotals.plus(other: StatisticsTotals): StatisticsTotals = copy(
    count = count + other.count, total = total + other.total, tax = tax + other.tax,
    missingTotal = missingTotal + other.missingTotal, missingTax = missingTax + other.missingTax,
    ignoredCount = ignoredCount + other.ignoredCount)

fun StatisticsResult.toBreakdown(request: StatisticsRequest): StatisticsGroupBreakdown {
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
