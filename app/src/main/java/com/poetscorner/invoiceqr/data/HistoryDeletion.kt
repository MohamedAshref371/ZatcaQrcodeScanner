package com.poetscorner.invoiceqr.data

import java.time.LocalDate
import java.time.ZoneId

enum class HistoryDeletionPeriod { ALL, THREE_DAYS, SEVEN_DAYS, ONE_MONTH, THREE_MONTHS, ONE_YEAR, TWO_YEARS }

data class HistoryDeletionPlan(
    val source: HistoryDateSource,
    val period: HistoryDeletionPeriod,
    val cutoffDay: Long?,
    val cutoffScanTime: Long?
) {
    companion object {
        fun create(source: HistoryDateSource, period: HistoryDeletionPeriod,
            today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): HistoryDeletionPlan {
            val cutoff = when (period) {
                HistoryDeletionPeriod.ALL -> null
                HistoryDeletionPeriod.THREE_DAYS -> today.minusDays(3)
                HistoryDeletionPeriod.SEVEN_DAYS -> today.minusDays(7)
                HistoryDeletionPeriod.ONE_MONTH -> today.minusMonths(1)
                HistoryDeletionPeriod.THREE_MONTHS -> today.minusMonths(3)
                HistoryDeletionPeriod.ONE_YEAR -> today.minusYears(1)
                HistoryDeletionPeriod.TWO_YEARS -> today.minusYears(2)
            }
            return HistoryDeletionPlan(source, period, cutoff?.toEpochDay(),
                cutoff?.atStartOfDay(zone)?.toInstant()?.toEpochMilli())
        }
    }
}
