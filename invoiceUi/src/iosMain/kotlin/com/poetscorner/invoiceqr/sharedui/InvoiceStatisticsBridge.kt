package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.InvoiceStatisticsAccumulator
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsKey
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsOptions
import com.poetscorner.invoiceqr.shared.StatisticsCategoryCalculation
import com.poetscorner.invoiceqr.shared.categoryBreakdown
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsTotals
import com.poetscorner.invoiceqr.shared.StatisticsGrouping
import kotlinx.datetime.LocalDate

class InvoiceStatisticsBuilder(private val query: InvoiceStatisticsQuery) {
    private val accumulator = InvoiceStatisticsAccumulator(query.options)

    fun add(seller: String, vat: String, invoiceDay: String, total: String, tax: String, category: Int) {
        val date = if (invoiceDay.isEmpty()) null else runCatching { LocalDate.parse(invoiceDay).toEpochDays() }.getOrNull()
        accumulator.add(seller, vat, date, total, tax, category)
    }

    fun finish(): InvoiceStatisticsSnapshot {
        val result = accumulator.finish()
        return InvoiceStatisticsSnapshot(result.summary.toDisplay(), result.groups.map {
            InvoiceStatisticsSection(title(it.key), it.totals.toDisplay(), it.key)
        })
    }

    private fun title(key: InvoiceStatisticsKey): String {
        val language = query.language
        val grouping = query.options.grouping
        if (grouping == StatisticsGrouping.CATEGORY) return invoiceCategoryLabel(key.text?.toIntOrNull() ?: 0, language)
        val start = key.periodStart ?: return key.text ?: language.text("غير محدد", "Unspecified")
        val date = LocalDate.fromEpochDays(start)
        return when (grouping) {
            StatisticsGrouping.DAY -> date.toString()
            StatisticsGrouping.MONTH -> date.toString().dropLast(3)
            StatisticsGrouping.QUARTER -> language.text("الربع ${(date.monthNumber - 1) / 3 + 1} · ${date.year}", "Q${(date.monthNumber - 1) / 3 + 1} · ${date.year}")
            StatisticsGrouping.HALF_YEAR -> language.text("النصف ${if (date.monthNumber == 1) 1 else 2} · ${date.year}", "H${if (date.monthNumber == 1) 1 else 2} · ${date.year}")
            else -> date.year.toString()
        }
    }

    private fun InvoiceStatisticsTotals.toDisplay() =
        InvoiceStatisticsAmounts(count, total.toPlainString(), tax.toPlainString(), missingTotal, missingTax, ignoredCount)
}

class InvoiceGroupBreakdownBuilder(private val query: InvoiceGroupBreakdownQuery) {
    private val accumulator = InvoiceStatisticsAccumulator(InvoiceStatisticsOptions(grouping = StatisticsGrouping.CATEGORY))

    fun add(total: String, tax: String, category: Int) = accumulator.add("", "", null, total, tax, category)

    fun finish(): InvoiceGroupBreakdownDisplay {
        val result = accumulator.finish().categoryBreakdown(query.calculations)
        val language = query.language
        fun amount(totals: InvoiceStatisticsTotals, tax: Boolean): String {
            val missing = if (tax) totals.missingTax else totals.missingTotal
            val contributing = totals.count - totals.ignoredCount
            if (contributing > 0 && missing == contributing) return language.text("غير متاح", "Unavailable")
            return (if (tax) totals.tax else totals.total).toPlainString()
        }
        fun amounts(original: InvoiceStatisticsTotals, net: InvoiceStatisticsTotals) =
            InvoiceBreakdownAmounts(amount(original, false), amount(net, false), amount(original, true), amount(net, true))
        return InvoiceGroupBreakdownDisplay(result.net.count, amounts(result.original, result.net), result.categories.map { line ->
            val symbol = when (line.calculation) {
                StatisticsCategoryCalculation.ADD -> "+"
                StatisticsCategoryCalculation.SUBTRACT -> "−"
                StatisticsCategoryCalculation.IGNORE -> "⊘"
            }
            val count = line.original.count
            val title = "$symbol ${invoiceCategoryLabel(line.category, language)} · " +
                language.text("عدد الفواتير: $count", "Invoices: $count")
            val notices = buildList {
                if (line.original.missingTotal > 0) add(language.text(
                    "${line.original.missingTotal} فواتير بدون إجمالي صالح", "${line.original.missingTotal} invoices have no valid total"))
                if (line.original.missingTax > 0) add(language.text(
                    "${line.original.missingTax} فواتير بدون ضريبة صالحة", "${line.original.missingTax} invoices have no valid tax amount"))
            }
            InvoiceBreakdownSection(title, amounts(line.original, line.contribution), notices)
        })
    }
}
