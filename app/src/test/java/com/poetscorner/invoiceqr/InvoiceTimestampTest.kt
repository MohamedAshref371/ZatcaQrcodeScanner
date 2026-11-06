package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.ui.formatInvoiceTimestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class InvoiceTimestampTest {
    @Test fun preservesInvoiceClockAndLabelsOnlyLocalOrUtc() {
        for ((suffix, label) in listOf("Z" to R.string.invoice_date_utc,
            "" to R.string.invoice_date_local)) {
            val display = requireNotNull(formatInvoiceTimestamp("2026-10-22T00:05:08$suffix", Locale.US))
            assertEquals("Thursday 22 October 2026", display.date)
            assertEquals("12:05:08 AM", display.time)
            assertEquals(label, display.titleLabel)
        }
    }

    @Test fun nonConformingTimestampsRemainAvailableForRawFallback() {
        for (value in listOf("", "invalid", "2026-02-30T15:45:08Z",
            "2026-10-22T15:45:08.123", "2026-10-22T15:45:08.123Z",
            "2026-10-22T15:45:08+03:00", "2026-10-22T15:45:08+00:00")) {
            assertNull(formatInvoiceTimestamp(value, Locale.US))
        }
    }
}
