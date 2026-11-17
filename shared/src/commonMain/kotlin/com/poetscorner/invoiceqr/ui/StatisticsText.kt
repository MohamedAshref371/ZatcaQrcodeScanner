package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.invoice_category_none
import com.poetscorner.invoiceqr.resources.stats_half_first
import com.poetscorner.invoiceqr.resources.stats_half_second
import com.poetscorner.invoiceqr.resources.stats_quarter_first
import com.poetscorner.invoiceqr.resources.stats_quarter_fourth
import com.poetscorner.invoiceqr.resources.stats_quarter_second
import com.poetscorner.invoiceqr.resources.stats_quarter_third
import com.poetscorner.invoiceqr.resources.stats_unknown_date
import com.poetscorner.invoiceqr.resources.stats_unknown_seller
import com.poetscorner.invoiceqr.resources.stats_unknown_vat
import androidx.compose.runtime.Composable
import com.poetscorner.invoiceqr.ui.text.stringResource
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.data.dateFromEpochDay
import kotlinx.datetime.number

@Composable
internal fun statisticsGroupTitle(request: StatisticsRequest, key: StatisticsKey): String {
    val grouping = request.grouping
    if (key.unknown) return stringResource(when (grouping) {
        StatisticsGrouping.SELLER -> Res.string.stats_unknown_seller
        StatisticsGrouping.VAT -> Res.string.stats_unknown_vat
        StatisticsGrouping.CATEGORY -> Res.string.invoice_category_none
        else -> Res.string.stats_unknown_date
    })
    if (grouping == StatisticsGrouping.CATEGORY) {
        val category = key.text?.toIntOrNull() ?: InvoiceCategories.NONE
        return stringResource(InvoiceCategories.resource(category))
    }
    key.text?.let { return it }
    val epochDay = requireNotNull(key.periodStart)
    val day = dateFromEpochDay(epochDay)
    val locale = uiLocaleTag()
    return when (grouping) {
        StatisticsGrouping.DAY -> formatDay(epochDay, locale)
        StatisticsGrouping.MONTH -> formatMonth(epochDay, locale)
        StatisticsGrouping.QUARTER -> stringResource(listOf(Res.string.stats_quarter_first, Res.string.stats_quarter_second,
            Res.string.stats_quarter_third, Res.string.stats_quarter_fourth)[(day.month.number - 1) / 3], day.year)
        StatisticsGrouping.HALF_YEAR -> stringResource(if (day.month.number <= 6) Res.string.stats_half_first else Res.string.stats_half_second, day.year)
        else -> day.year.toString()
    }
}
