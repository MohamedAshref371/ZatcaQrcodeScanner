package com.poetscorner.invoiceqr.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())
internal fun dateFromEpochDay(day: Long) = LocalDate.fromEpochDays(day)
internal fun startOfDayMillis(day: Long, zone: TimeZone, next: Boolean = false): Long {
    val date = dateFromEpochDay(day)
    return (if (next) date.plus(1, DateTimeUnit.DAY) else date).atStartOfDayIn(zone).toEpochMilliseconds()
}

internal fun String.normalizeDigits(): String = map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
