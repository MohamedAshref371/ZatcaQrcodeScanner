package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.ui.formatInvoiceTimestamp
import org.junit.Assert.*
import org.junit.Test
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.Locale

class InvoiceTimestampTest {
    @Test fun preservesInvoiceClockAndOnlyLabelsExplicitKnownZones() {
        for ((suffix, label) in listOf(
            "Z" to R.string.invoice_date_gmt,
            "+03:00" to R.string.invoice_date_makkah,
            "+0300" to R.string.invoice_date_makkah,
            "" to R.string.invoice_date_time,
            "+05:30" to R.string.invoice_date_time,
            "-04:00" to R.string.invoice_date_time
        )) {
            val display = requireNotNull(formatInvoiceTimestamp("2026-10-22T00:05:08$suffix", Locale.US))
            assertEquals("22 October 2026", display.date)
            assertEquals("12:05:08 AM", display.time)
            assertEquals(label, display.titleLabel)
        }
    }

    @Test fun localizesDateAndRetainsSecondsWithFractionalInput() {
        val locale = Locale.forLanguageTag("ar")
        val display = requireNotNull(formatInvoiceTimestamp("2026-10-22T15:45:08.123+03:00", locale))
        assertEquals("22 أكتوبر 2026", display.date)
        assertEquals(LocalDateTime.of(2026, 10, 22, 15, 45, 8)
            .format(DateTimeFormatter.ofPattern("h:mm:ss a", locale)), display.time)
    }

    @Test fun invalidTimestampsRemainAvailableForRawFallback() {
        for (value in listOf("", "invalid", "2026-02-30T15:45:08Z")) {
            assertNull(formatInvoiceTimestamp(value, Locale.US))
        }
    }
}
