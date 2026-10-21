package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.croppedNv21
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class GrayFrameTest {
    @Test fun cropExcludesSurroundingsAndRowPadding() {
        val plane = ByteBuffer.wrap(ByteArray(48) { it.toByte() })
        plane.position(7)
        val frame = croppedNv21(plane, 8, 1, 2, 1, 4, 4)
        assertEquals(4, frame.width)
        assertEquals(4, frame.height)
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 18, 19, 20, 21,
            26, 27, 28, 29, 34, 35, 36, 37), frame.bytes.copyOf(16))
        assertTrue(frame.bytes.drop(16).all { it == 128.toByte() })
        assertEquals(24, frame.bytes.size)
        assertEquals(7, plane.position())
    }

    @Test fun supportsPixelStrideAndRoundsOddCropInward() {
        val plane = ByteBuffer.wrap(ByteArray(96) { it.toByte() })
        val frame = croppedNv21(plane, 16, 2, 1, 1, 5, 3)
        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(byteArrayOf(18, 20, 22, 24, 34, 36, 38, 40), frame.bytes.copyOf(8))
        assertEquals(12, frame.bytes.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnEmptyChromaBlock() {
        croppedNv21(ByteBuffer.allocate(16), 4, 1, 0, 0, 1, 1)
    }
}
