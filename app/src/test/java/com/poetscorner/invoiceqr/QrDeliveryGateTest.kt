package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.QrDeliveryGate
import org.junit.Assert.*
import org.junit.Test

class QrDeliveryGateTest {
    @Test fun continuousNeverRepeatsAVisibleCodeEvenAfterLongDelays() {
        val gate = QrDeliveryGate(true)
        assertTrue(gate.shouldDeliver("AQID==", 0))
        assertFalse(gate.shouldDeliver("AQID==", 4000))
        assertFalse(gate.shouldDeliver(" A Q I D\n", 20000))
        assertTrue(gate.shouldDeliver("another QR", 20001))
    }
    @Test fun briefDecodeMissesDoNotRearmButRemovingAndReturningTheCodeDoes() {
        val gate = QrDeliveryGate(true)
        assertTrue(gate.shouldDeliver("one", 0))
        assertFalse(gate.shouldDeliver(null, 100))
        assertFalse(gate.shouldDeliver("one", 500))
        assertFalse(gate.shouldDeliver(null, 1000))
        assertFalse(gate.shouldDeliver(null, 1400))
        assertFalse(gate.shouldDeliver(null, 1800))
        assertTrue(gate.shouldDeliver("one", 1900))
        assertFalse(gate.shouldDeliver("one", 2000))
    }
    @Test fun aDifferentCodeIsAcceptedImmediatelyAndNormalModeKeepsItsRetryDelay() {
        val batch = QrDeliveryGate(true)
        assertTrue(batch.shouldDeliver("one", 0))
        assertTrue(batch.shouldDeliver("two", 1))
        val normal = QrDeliveryGate(false)
        assertTrue(normal.shouldDeliver("invalid", 0))
        assertFalse(normal.shouldDeliver(null, 500))
        assertFalse(normal.shouldDeliver("invalid", 3999))
        assertTrue(normal.shouldDeliver("invalid", 4000))
        assertTrue(normal.shouldDeliver("different", 4001))
    }
}
