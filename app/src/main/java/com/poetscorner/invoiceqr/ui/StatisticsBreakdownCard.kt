package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.InvoiceCategories
import com.poetscorner.invoiceqr.data.label
import com.poetscorner.invoiceqr.data.StatisticsCategoryCalculation
import com.poetscorner.invoiceqr.data.StatisticsGroupBreakdown
import com.poetscorner.invoiceqr.data.StatisticsTotals
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
        return money.format((if (tax) totals.tax else totals.total).toPlainString().toBigDecimal())
    }
    @Composable fun amountRow(label: String, original: StatisticsTotals, contribution: StatisticsTotals,
        tax: Boolean, emphasized: Boolean = false) {
        val style = if (emphasized) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, Modifier.weight(1.15f), style = style)
            Text(amount(original, tax), Modifier.weight(1f), style = style, textAlign = TextAlign.Center)
            Text(amount(contribution, tax), Modifier.weight(1f), style = style,
                textAlign = TextAlign.Center, fontWeight = if (emphasized) FontWeight.SemiBold else null)
        }
    }
    Card(Modifier.fillMaxWidth().testTag("statistics_breakdown"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stats_breakdown_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.stats_count, breakdown.net.count), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1.15f))
                Text(stringResource(R.string.stats_breakdown_original), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.stats_breakdown_contribution), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
            amountRow(stringResource(R.string.stats_total_including_tax), breakdown.original, breakdown.net,
                tax = false, emphasized = true)
            amountRow(stringResource(R.string.tag_tax), breakdown.original, breakdown.net,
                tax = true, emphasized = true)
            breakdown.categories.forEach { line ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val symbol = when (line.calculation) {
                    StatisticsCategoryCalculation.ADD -> "+"
                    StatisticsCategoryCalculation.SUBTRACT -> "−"
                    StatisticsCategoryCalculation.IGNORE -> "⊘"
                }
                Text(stringResource(R.string.stats_breakdown_category_line, symbol,
                    stringResource(InvoiceCategories.label(line.category)),
                    stringResource(R.string.stats_count, line.original.count)),
                    style = MaterialTheme.typography.titleSmall)
                amountRow(stringResource(R.string.stats_total_including_tax), line.original, line.contribution, tax = false)
                amountRow(stringResource(R.string.tag_tax), line.original, line.contribution, tax = true)
                if (line.original.missingTotal > 0) Text(
                    stringResource(R.string.stats_breakdown_missing_total, line.original.missingTotal),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (line.original.missingTax > 0) Text(
                    stringResource(R.string.stats_breakdown_missing_tax, line.original.missingTax),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
