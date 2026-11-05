package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryDeletionPeriod
import com.poetscorner.invoiceqr.data.HistoryDeletionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryDeletionPlanTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val zone = ZoneId.of("Africa/Cairo")

    @Test fun periodsUseCalendarDatesAndLocalMidnight() {
        val expected = mapOf(
            HistoryDeletionPeriod.THREE_DAYS to today.minusDays(3),
            HistoryDeletionPeriod.SEVEN_DAYS to today.minusDays(7),
            HistoryDeletionPeriod.ONE_MONTH to today.minusMonths(1),
            HistoryDeletionPeriod.THREE_MONTHS to today.minusMonths(3),
            HistoryDeletionPeriod.ONE_YEAR to today.minusYears(1),
            HistoryDeletionPeriod.TWO_YEARS to today.minusYears(2)
        )
        expected.forEach { (period, cutoff) ->
            val plan = HistoryDeletionPlan.create(HistoryDateSource.SCAN, period, today, zone)
            assertEquals(cutoff.toEpochDay(), plan.cutoffDay)
            assertEquals(cutoff.atStartOfDay(zone).toInstant().toEpochMilli(), plan.cutoffScanTime)
        }
    }

    @Test fun allHasNoCutoff() {
        val plan = HistoryDeletionPlan.create(HistoryDateSource.INVOICE,
            HistoryDeletionPeriod.ALL, today, zone)
        assertNull(plan.cutoffDay)
        assertNull(plan.cutoffScanTime)
    }
}
