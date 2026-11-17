package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.domain.ExactAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExactAmountTest {
    private fun amount(value: String) = requireNotNull(ExactAmount.parseInvoiceAmount(value))

    @Test fun additionAndCancellationStayExactBeyondLongCents() {
        val oneCent = amount("0.01")
        val maxLongCents = amount("92233720368547758.07")
        val beyondLong = maxLongCents + oneCent
        assertEquals("92233720368547758.08", beyondLong.toPlainString())
        assertEquals("92233720368547758.07", (beyondLong + -oneCent).toPlainString())
        assertEquals("-92233720368547758.09", (-beyondLong + -oneCent).toPlainString())
        assertEquals("-0.01", (maxLongCents + -beyondLong).toPlainString())
        assertEquals(ExactAmount.ZERO, beyondLong + -beyondLong)
        assertTrue(-beyondLong < -maxLongCents)
    }

    @Test fun combinesInvoiceAmountsWithoutDroppingDigits() {
        assertEquals("10000000000000000403.29",
            (amount("00010000000000000000000.99") + amount("402.30")).toPlainString())
        assertEquals("100000000000000000000.00",
            (amount("99999999999999999999.99") + amount("0.01")).toPlainString())
    }
}
