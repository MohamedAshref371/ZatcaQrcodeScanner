package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.MultiFrameConsensus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MultiFrameConsensusTest {
    private val side = 64

    private fun pattern(invert: Boolean = false) = ByteArray(side * side) { index ->
        val x = index % side
        val y = index / side
        val dark = ((x / 4 + y / 4) % 2 == 0) != invert
        if (dark) 0 else 255.toByte()
    }

    @Test fun recoveryIsOptIn() {
        assertFalse(ScannerSettings().multiFrameRecovery)
    }

    @Test fun firstFusionUsesTwoFramesAndOneDecode() {
        val consensus = MultiFrameConsensus(side)
        var calls = 0
        assertNull(consensus.offer(pattern(), 0L) { calls++; "invoice" })
        assertEquals(0, calls)
        assertEquals("invoice", consensus.offer(pattern(), 300L) { calls++; "invoice" })
        assertEquals(1, calls)
        assertNull(consensus.offer(pattern(), 600L) { calls++; "invoice" })
        assertEquals(1, calls)
    }

    @Test fun failedFusionRetriesUpToFourFrames() {
        val consensus = MultiFrameConsensus(side)
        var calls = 0
        repeat(3) { index ->
            assertNull(consensus.offer(pattern(), index * 300L) {
                calls++
                null
            })
        }
        assertEquals(2, calls)
        assertEquals("invoice", consensus.offer(pattern(), 900L) {
            calls++
            "invoice"
        })
        assertEquals(3, calls)
    }

    @Test fun fastFramesDoNotTriggerFusionOnEveryFrame() {
        val consensus = MultiFrameConsensus(side)
        var calls = 0
        val decode: (ByteArray) -> String? = { calls++; null }
        assertNull(consensus.offer(pattern(), 0L, decode))
        assertNull(consensus.offer(pattern(), 100L, decode))
        assertNull(consensus.offer(pattern(), 150L, decode))
        assertNull(consensus.offer(pattern(), 200L, decode))
        assertEquals(1, calls)
        assertNull(consensus.offer(pattern(), 400L, decode))
        assertEquals(2, calls)
    }

    @Test fun differentSymbolOrStaleFrameClearsCollection() {
        val consensus = MultiFrameConsensus(side)
        var calls = 0
        val decode: (ByteArray) -> String? = { calls++; null }
        assertNull(consensus.offer(pattern(), 0L, decode))
        assertNull(consensus.offer(pattern(), 100L, decode))
        assertEquals(1, calls)
        assertNull(consensus.offer(pattern(invert = true), 200L, decode))
        assertEquals(1, calls)
        assertNull(consensus.offer(pattern(invert = true), 300L, decode))
        assertEquals(2, calls)
        assertNull(consensus.offer(pattern(), 1600L, decode))
        assertEquals(2, calls)
    }
}
