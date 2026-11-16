package com.poetscorner.invoiceqr.shared

data class InvoiceCategoryContribution(val category: Int, val calculation: StatisticsCategoryCalculation,
    val original: InvoiceStatisticsTotals, val contribution: InvoiceStatisticsTotals)

data class InvoiceGroupBreakdown(val original: InvoiceStatisticsTotals, val net: InvoiceStatisticsTotals,
    val categories: List<InvoiceCategoryContribution>)

fun InvoiceStatisticsResult.categoryBreakdown(calculations: Map<Int, StatisticsCategoryCalculation>): InvoiceGroupBreakdown {
    val groups = groups.associateBy { it.key.text?.toIntOrNull() ?: InvoiceAnnotationRules.NO_CATEGORY }
    val ordered = InvoiceAnnotationRules.categoryIds + groups.keys.filterNot { it in InvoiceAnnotationRules.categoryIds }
    val categories = ordered.mapNotNull { category ->
        val original = groups[category]?.totals ?: return@mapNotNull null
        val calculation = calculations[category] ?: StatisticsCategoryCalculation.ADD
        val contribution = when (calculation) {
            StatisticsCategoryCalculation.ADD -> original
            StatisticsCategoryCalculation.SUBTRACT -> original.copy(total = -original.total, tax = -original.tax)
            StatisticsCategoryCalculation.IGNORE -> InvoiceStatisticsTotals(count = original.count, ignoredCount = original.count)
        }
        InvoiceCategoryContribution(category, calculation, original, contribution)
    }
    val net = categories.fold(InvoiceStatisticsTotals()) { sum, line ->
        val value = line.contribution
        InvoiceStatisticsTotals(sum.count + value.count, sum.total + value.total, sum.tax + value.tax,
            sum.missingTotal + value.missingTotal, sum.missingTax + value.missingTax, sum.ignoredCount + value.ignoredCount)
    }
    return InvoiceGroupBreakdown(summary, net, categories)
}
