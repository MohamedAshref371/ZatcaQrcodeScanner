package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ReviewUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewUsageTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1000L
    private fun qualified() = (1..10).fold(ReviewUsage()
        .used(start, "2026-10-01").used(start + day, "2026-10-02")
        .used(start + 2 * day, "2026-10-03")) { usage, id -> usage.saved("hash-$id") }

    @Test fun requiresAllThreeThresholds() {
        val usage = qualified()
        assertFalse(usage.eligible(start + 7 * day - 1))
        assertTrue(usage.eligible(start + 7 * day))
        assertFalse(usage.copy(activeDays = setOf("one", "two")).eligible(start + 7 * day))
        assertFalse(usage.copy(invoiceKeys = usage.invoiceKeys.take(9).toSet()).eligible(start + 7 * day))
        assertFalse(usage.copy(firstUsedAt = null).eligible(start + 7 * day))
        assertFalse(usage.eligible(start - 1))
    }

    @Test fun oneAttemptStaysConsumedEvenAfterMoreUsage() {
        assertFalse(qualified().copy(attempted = true)
            .used(start + 365 * day, "2027-10-01").saved("another")
            .eligible(start + 365 * day))
    }

    @Test fun repeatedDaysAndInvoicesDoNotAdvanceThresholds() {
        val usage = (1..20).fold(ReviewUsage()) { current, _ ->
            current.used(start, "2026-10-01").saved("same-invoice")
        }
        assertEquals(1, usage.activeDays.size)
        assertEquals(1, usage.invoiceKeys.size)
        assertFalse(usage.eligible(start + 7 * day))
    }

    @Test fun trackingIsBoundedAndKeepsFirstUse() {
        val usage = (1..100).fold(ReviewUsage()) { current, id ->
            current.used(start + id * day, "day-$id").saved("hash-$id")
        }
        assertEquals(start + day, usage.firstUsedAt)
        assertEquals(3, usage.activeDays.size)
        assertEquals(10, usage.invoiceKeys.size)
    }
}
