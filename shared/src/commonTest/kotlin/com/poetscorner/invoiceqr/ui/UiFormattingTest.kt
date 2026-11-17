package com.poetscorner.invoiceqr.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class UiFormattingTest {
    @Test fun formatIntegerSupportsTheFullLongRange() {
        assertEquals("9,223,372,036,854,775,807", formatInteger(Long.MAX_VALUE, "en-US"))
        assertEquals("9223372036854775807", formatInteger(Long.MAX_VALUE, "en-US", grouped = false))
    }

    @Test fun historyTotalsKeepEveryDigitBeyondNativeDecimalPrecision() {
        val amount = "-123456789012345678901234567890123456789012345.07"
        val expected = "-123,456,789,012,345,678,901,234,567,890,123,456,789,012,345.07"
        assertEquals(expected, formatDecimal(amount, "en-US"))
    }

    @Test fun dateLabelsPreserveTheInvoiceClockAndCalendarDay() {
        val day = LocalDate(2026, 1, 1).toEpochDays()
        assertEquals("Jan 1, 2026", formatDay(day, "en-US"))
        assertEquals("January 2026", formatMonth(day, "en-US"))
        val date = formatInvoiceDateTime(LocalDateTime(2026, 1, 1, 0, 7, 5), "en-US").first
        assertEquals("Thursday 1 January 2026", date)
    }
}
