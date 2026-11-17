package com.poetscorner.invoiceqr.data

import kotlinx.datetime.*

enum class HistoryDeletionPeriod { ALL, THREE_DAYS, SEVEN_DAYS, ONE_MONTH, THREE_MONTHS, ONE_YEAR, TWO_YEARS }

data class HistoryDeletionPlan(
    val source: HistoryDateSource,
    val period: HistoryDeletionPeriod,
    val cutoffDay: Long?,
    val cutoffScanTime: Long?
) {
    companion object {
        fun create(source: HistoryDateSource, period: HistoryDeletionPeriod,
            today: LocalDate = today(), zone: TimeZone = TimeZone.currentSystemDefault()): HistoryDeletionPlan {
            val cutoff = when (period) {
                HistoryDeletionPeriod.ALL -> null
                HistoryDeletionPeriod.THREE_DAYS -> today.minus(3, DateTimeUnit.DAY)
                HistoryDeletionPeriod.SEVEN_DAYS -> today.minus(7, DateTimeUnit.DAY)
                HistoryDeletionPeriod.ONE_MONTH -> today.minus(1, DateTimeUnit.MONTH)
                HistoryDeletionPeriod.THREE_MONTHS -> today.minus(3, DateTimeUnit.MONTH)
                HistoryDeletionPeriod.ONE_YEAR -> today.minus(1, DateTimeUnit.YEAR)
                HistoryDeletionPeriod.TWO_YEARS -> today.minus(2, DateTimeUnit.YEAR)
            }
            return HistoryDeletionPlan(source, period, cutoff?.toEpochDays(),
                cutoff?.atStartOfDayIn(zone)?.toEpochMilliseconds())
        }
    }
}
