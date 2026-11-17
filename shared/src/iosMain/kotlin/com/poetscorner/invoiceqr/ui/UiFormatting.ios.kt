package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import platform.Foundation.*

@Composable
internal actual fun uiLocaleTag(): String = Locale.current.toLanguageTag()

private fun numberFormatter(language: String, grouped: Boolean, fractionDigits: ULong) =
    NSNumberFormatter().apply {
        locale = NSLocale(localeIdentifier = language)
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = grouped
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }

internal actual fun formatInteger(value: Long, locale: String, grouped: Boolean): String =
    numberFormatter(locale, grouped, 0uL).stringFromNumber(NSNumber(longLong = value)) ?: value.toString()

internal actual fun formatDecimal(value: String, locale: String): String {
    val format = numberFormatter(locale, true, 2uL)
    // Map decimal digits directly so totals outside NSDecimalNumber's precision stay exact.
    val digits = numberFormatter(locale, false, 0uL)
    val localized = (0..9).map { digits.stringFromNumber(NSNumber(int = it)).orEmpty() }
    val negative = value.startsWith('-')
    val parts = value.removePrefix("-").split('.', limit = 2)
    val size = format.groupingSize.toInt().coerceAtLeast(1)
    val secondarySize = format.secondaryGroupingSize.toInt().takeIf { it > 0 } ?: size
    fun localize(text: String) = text.map { localized[it.digitToInt()] }.joinToString("")
    val whole = parts[0]
    val prefix = whole.dropLast(size).reversed().chunked(secondarySize).asReversed().map { it.reversed() }
    val groups = prefix + whole.takeLast(size)
    return (if (negative) format.negativePrefix.orEmpty() else format.positivePrefix.orEmpty()) +
        groups.joinToString(format.groupingSeparator.orEmpty()) { localize(it) } +
        format.decimalSeparator.orEmpty() + localize(parts[1]) +
        (if (negative) format.negativeSuffix.orEmpty() else format.positiveSuffix.orEmpty())
}

private fun dateFormatter(language: String, utc: Boolean = true) = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = language)
    calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    if (utc) timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
}

private fun date(seconds: Double) = NSDate(timeIntervalSince1970 = seconds)

internal actual fun formatDay(epochDay: Long, locale: String, short: Boolean): String =
    dateFormatter(locale).apply {
        dateStyle = if (short) NSDateFormatterShortStyle else NSDateFormatterMediumStyle
    }.stringFromDate(date(epochDay * 86_400.0))

internal actual fun formatMonth(epochDay: Long, locale: String): String =
    dateFormatter(locale).apply { dateFormat = "MMMM yyyy" }
        .stringFromDate(date(epochDay * 86_400.0))

internal actual fun formatCheckedAt(millis: Long, locale: String): String =
    dateFormatter(locale, utc = false).apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterMediumStyle
    }.stringFromDate(date(millis / 1_000.0))

internal actual fun formatInvoiceDateTime(value: LocalDateTime, locale: String): Pair<String, String> {
    val date = date(value.toInstant(TimeZone.UTC).epochSeconds.toDouble())
    val format = dateFormatter(locale)
    format.dateFormat = "EEEE d MMMM yyyy"
    val day = format.stringFromDate(date)
    format.dateFormat = "h:mm:ss a"
    return day to format.stringFromDate(date)
}
