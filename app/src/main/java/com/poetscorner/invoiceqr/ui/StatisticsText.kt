package com.poetscorner.invoiceqr.ui

import android.content.Context
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun statisticsGroupTitle(context: Context, grouping: StatisticsGrouping, key: StatisticsKey): String {
    if (key.unknown) return context.getString(when (grouping) {
        StatisticsGrouping.SELLER -> R.string.stats_unknown_seller
        StatisticsGrouping.VAT -> R.string.stats_unknown_vat
        StatisticsGrouping.CATEGORY -> R.string.invoice_category_none
        else -> R.string.stats_unknown_date
    })
    if (grouping == StatisticsGrouping.CATEGORY) return context.getString(
        InvoiceCategories.label(key.text?.toIntOrNull() ?: InvoiceCategories.NONE))
    key.text?.let { return it }
    val day = LocalDate.ofEpochDay(requireNotNull(key.periodStart))
    val locale = context.resources.configuration.locales[0]
    return when (grouping) {
        StatisticsGrouping.DAY -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)
        StatisticsGrouping.MONTH -> DateTimeFormatter.ofPattern("MMMM uuuu", locale).format(day)
        StatisticsGrouping.QUARTER -> context.getString(listOf(R.string.stats_quarter_first, R.string.stats_quarter_second,
            R.string.stats_quarter_third, R.string.stats_quarter_fourth)[(day.monthValue - 1) / 3], day.year)
        StatisticsGrouping.HALF_YEAR -> context.getString(if (day.monthValue <= 6) R.string.stats_half_first else R.string.stats_half_second, day.year)
        else -> day.year.toString()
    }
}
