package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.invoicePayloadKey
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoicePayloadKeyTest {
    @Test fun keepsTheAndroidHashForNormalizedBase64AndNonAsciiInput() {
        assertEquals("68ea8980f830e1ef7323feea7580371fb7fd42384db988994fa12865491894d0",
            invoicePayloadKey(" A Q\nF B == "))
        assertEquals("82fefb70f47227e68b9b2412293703fc17b5933e2e602ac19dd84a5a82690d2c",
            invoicePayloadKey("Aé🙂="))
    }
}
