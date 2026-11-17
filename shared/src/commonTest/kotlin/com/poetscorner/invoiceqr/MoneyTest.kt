package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun parsesCentsExactly() {
        assertEquals(Money(0), Money.parseOrNull("0.00"))
        assertEquals(Money(1200), Money.parseOrNull("12"))
        assertEquals(Money(1230), Money.parseOrNull("012.3"))
        assertEquals(Money(-1), Money.parseOrNull("-0.01"))
        assertEquals(Money(Long.MAX_VALUE), Money.parseOrNull("92233720368547758.07"))
        assertEquals(Money(Long.MIN_VALUE), Money.parseOrNull("-92233720368547758.08"))
    }

    @Test fun rejectsOverflowAndNonDecimalFormats() {
        for (value in listOf("92233720368547758.08", "-92233720368547758.09", "0.001", "0.",
            "1,00", "1e2", " 1", "+1", "")) {
            assertNull(Money.parseOrNull(value), value)
        }
    }
}
