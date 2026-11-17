package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

@Composable
internal actual fun uiLocaleTag(): String = LocalConfiguration.current.locales[0].toLanguageTag()

internal actual fun formatInteger(value: Long, locale: String, grouped: Boolean): String =
    NumberFormat.getIntegerInstance(Locale.forLanguageTag(locale)).apply {
        isGroupingUsed = grouped
    }.format(value)

internal actual fun formatDecimal(value: String, locale: String): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag(locale)).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value.toBigDecimal())

internal actual fun formatDay(epochDay: Long, locale: String, short: Boolean): String =
    DateTimeFormatter.ofLocalizedDate(if (short) FormatStyle.SHORT else FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag(locale)).format(LocalDate.ofEpochDay(epochDay))

internal actual fun formatMonth(epochDay: Long, locale: String): String =
    DateTimeFormatter.ofPattern("MMMM uuuu", Locale.forLanguageTag(locale))
        .format(LocalDate.ofEpochDay(epochDay))

internal actual fun formatCheckedAt(millis: Long, locale: String): String =
    DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.forLanguageTag(locale))
        .format(Date(millis))

internal actual fun formatInvoiceDateTime(value: LocalDateTime, locale: String): Pair<String, String> {
    val dateTime = value.toJavaLocalDateTime()
    val language = Locale.forLanguageTag(locale)
    return dateTime.format(DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", language)) to
        dateTime.format(DateTimeFormatter.ofPattern("h:mm:ss a", language))
}
