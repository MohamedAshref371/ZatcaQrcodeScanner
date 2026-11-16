package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.InvoiceCategories
import com.poetscorner.invoiceqr.data.StatisticsCategoryCalculation
import com.poetscorner.invoiceqr.data.StatisticsGroupBreakdown
import com.poetscorner.invoiceqr.data.StatisticsTotals
import com.poetscorner.invoiceqr.sharedui.InvoiceBreakdownAmounts
import com.poetscorner.invoiceqr.sharedui.InvoiceBreakdownSection
import com.poetscorner.invoiceqr.sharedui.InvoiceStatisticsBreakdownCard
import java.text.NumberFormat

@Composable
internal fun StatisticsBreakdownCard(breakdown: StatisticsGroupBreakdown) {
    val locale = LocalConfiguration.current.locales[0]
    val money = remember(locale) { NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    } }
    @Composable fun amount(totals: StatisticsTotals, tax: Boolean): String {
        val missing = if (tax) totals.missingTax else totals.missingTotal
        val contributing = totals.count - totals.ignoredCount
        if (contributing > 0 && missing == contributing) return stringResource(R.string.history_total_unavailable)
        return money.format(if (tax) totals.tax else totals.total)
    }
    @Composable fun amounts(original: StatisticsTotals, net: StatisticsTotals) =
        InvoiceBreakdownAmounts(amount(original, false), amount(net, false), amount(original, true), amount(net, true))

    val sections = breakdown.categories.map { line ->
        val symbol = when (line.calculation) {
            StatisticsCategoryCalculation.ADD -> "+"
            StatisticsCategoryCalculation.SUBTRACT -> "−"
            StatisticsCategoryCalculation.IGNORE -> "⊘"
        }
        val missingTotal = if (line.original.missingTotal > 0)
            stringResource(R.string.stats_breakdown_missing_total, line.original.missingTotal) else null
        val missingTax = if (line.original.missingTax > 0)
            stringResource(R.string.stats_breakdown_missing_tax, line.original.missingTax) else null
        InvoiceBreakdownSection(stringResource(R.string.stats_breakdown_category_line, symbol,
            stringResource(InvoiceCategories.label(line.category)), stringResource(R.string.stats_count, line.original.count)),
            amounts(line.original, line.contribution), listOfNotNull(missingTotal, missingTax))
    }
    InvoiceStatisticsBreakdownCard(stringResource(R.string.stats_breakdown_title), stringResource(R.string.stats_count, breakdown.net.count),
        stringResource(R.string.stats_breakdown_original), stringResource(R.string.stats_breakdown_contribution),
        stringResource(R.string.stats_total_including_tax), stringResource(R.string.tag_tax),
        amounts(breakdown.original, breakdown.net), sections)
}
