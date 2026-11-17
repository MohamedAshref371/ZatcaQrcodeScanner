package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.resources.*
import com.poetscorner.invoiceqr.ui.text.UiText
import com.poetscorner.invoiceqr.ui.text.resolve
import kotlinx.datetime.number
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getString

internal suspend fun historyExportText(environment: ResourceEnvironment): HistoryExportText {
    val names = mapOf(1 to Res.string.tag_seller, 2 to Res.string.tag_vat,
        3 to Res.string.export_invoice_date, 4 to Res.string.tag_total,
        5 to Res.string.tag_tax, 6 to Res.string.tag_hash, 7 to Res.string.invoice_number,
        8 to Res.string.invoice_category, 9 to Res.string.invoice_note)
        .mapValues { getString(environment, it.value) }
    val categories = InvoiceCategories.ids.associateWith { getString(environment, InvoiceCategories.resource(it)) }
    return HistoryExportText(names) { categories.getValue(it) }
}

internal suspend fun statisticsExportText(environment: ResourceEnvironment, locale: String,
    request: StatisticsRequest, result: StatisticsResult): StatisticsExportText {
    val grouping = when(request.grouping) {
        StatisticsGrouping.SELLER -> Res.string.tag_seller
        StatisticsGrouping.VAT -> Res.string.tag_vat
        StatisticsGrouping.CATEGORY -> Res.string.invoice_category
        StatisticsGrouping.DAY -> Res.string.stats_day
        StatisticsGrouping.MONTH -> Res.string.stats_month
        StatisticsGrouping.QUARTER -> Res.string.stats_quarter
        StatisticsGrouping.HALF_YEAR -> Res.string.stats_half_year
        StatisticsGrouping.YEAR -> Res.string.stats_year
    }
    val net = request.categoryCalculationActive
    val headers = listOf(grouping, Res.string.stats_count_label,
        if (net) {
            if (result.summary.missingTotal > 0) Res.string.stats_available_net_total else Res.string.stats_net_total
        } else if (result.summary.missingTotal > 0) Res.string.stats_available_total else Res.string.stats_total_including_tax,
        if (net) {
            if (result.summary.missingTax > 0) Res.string.stats_available_net_tax else Res.string.stats_net_tax
        } else if (result.summary.missingTax > 0) Res.string.stats_available_tax else Res.string.tag_tax,
        Res.string.stats_missing_total_count, Res.string.stats_missing_tax_count).map { getString(environment, it) }
    val labels = result.groups.associate { group ->
        val key = group.key
        val title = when {
            key.unknown -> getString(environment, when(request.grouping) {
                StatisticsGrouping.SELLER -> Res.string.stats_unknown_seller
                StatisticsGrouping.VAT -> Res.string.stats_unknown_vat
                StatisticsGrouping.CATEGORY -> Res.string.invoice_category_none
                else -> Res.string.stats_unknown_date
            })
            request.grouping == StatisticsGrouping.CATEGORY -> getString(environment,
                InvoiceCategories.resource(key.text?.toIntOrNull() ?: InvoiceCategories.NONE))
            key.text != null -> key.text
            else -> {
                val epoch = requireNotNull(key.periodStart)
                val day = dateFromEpochDay(epoch)
                when(request.grouping) {
                    StatisticsGrouping.DAY -> formatDay(epoch, locale)
                    StatisticsGrouping.MONTH -> formatMonth(epoch, locale)
                    StatisticsGrouping.QUARTER -> UiText(listOf(Res.string.stats_quarter_first,
                        Res.string.stats_quarter_second, Res.string.stats_quarter_third,
                        Res.string.stats_quarter_fourth)[(day.month.number - 1) / 3], day.year).resolve(environment, locale)
                    StatisticsGrouping.HALF_YEAR -> UiText(if(day.month.number <= 6) Res.string.stats_half_first
                        else Res.string.stats_half_second, day.year).resolve(environment, locale)
                    else -> day.year.toString()
                }
            }
        }
        key to title
    }
    return StatisticsExportText(headers, getString(environment, Res.string.stats_summary)) { labels.getValue(it) }
}
