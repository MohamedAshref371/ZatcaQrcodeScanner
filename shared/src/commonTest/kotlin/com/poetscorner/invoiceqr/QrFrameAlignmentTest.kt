package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrFrameAlignmentTest {
    @Test fun rotationsUseCropLocalCoordinates() {
        val input: (Int, Int) -> Byte = { x, y -> (y * 3 + x + 1).toByte() }
        val rotations = mapOf(
            0 to byteArrayOf(1, 2, 3, 4, 5, 6),
            90 to byteArrayOf(4, 1, 5, 2, 6, 3),
            180 to byteArrayOf(6, 5, 4, 3, 2, 1),
            270 to byteArrayOf(3, 6, 2, 5, 1, 4),
        )
        for ((rotation, expected) in rotations) {
            val (luma, step) = sampleUprightLuma(3, 2, rotation, input)
            assertEquals(1, step)
            assertEquals(if (rotation % 180 == 0) 3 else 2, luma.width)
            assertContentEquals(expected, luma.pixels)
        }
    }

    @Test fun perspectiveWarpPreservesDarkAndLightRegionsAndRejectsFlatFrames() {
        val corners = QrPosition(QrPoint(8, 4), QrPoint(148, 12), QrPoint(154, 147), QrPoint(4, 152))
        val pixels = ByteArray(160 * 160) { if (it % 160 < 80) 45 else 220.toByte() }
        val aligned = assertNotNull(alignQrLuma(LumaImage(pixels, 160, 160), corners, 1, 128))
        assertEquals(0.toByte(), aligned[64 * 128 + 16])
        assertEquals(255.toByte(), aligned[64 * 128 + 112])
        assertNull(alignQrLuma(LumaImage(ByteArray(160 * 160) { 120 }, 160, 160), corners, 1, 128))
    }

    @Test fun consensusDeliversBinarizedLumaWithAWhiteQuietZone() {
        val recovery = MultiFrameQrRecovery()
        val pixel: (Int, Int) -> Byte = { x, y -> if ((x / 9 + y / 9) % 2 == 0) 30 else 210.toByte() }
        assertNull(recovery.offer(160, 160, 0, null, 100, true, pixel) { error("Needs multiple frames") })
        val result = recovery.offer(160, 160, 0, null, 200, true, pixel) {
            assertEquals(576, it.width)
            assertTrue(it.pixels.take(it.width * 32).all { value -> value == 255.toByte() })
            assertTrue(it.pixels.any { value -> value == 0.toByte() })
            "recovered"
        }
        assertEquals("recovered", result)
    }
}
