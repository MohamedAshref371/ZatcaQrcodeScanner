package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.croppedNv21
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraLumaBufferTest {
    @Test fun absoluteLumaReadsPreserveBufferPosition() {
        val plane = ByteBuffer.wrap(ByteArray(48) { it.toByte() })
        plane.position(7)
        val frame = croppedNv21(plane::get, 8, 1, 2, 1, 4, 4)
        assertEquals(10.toByte(), frame.bytes.first())
        assertEquals(7, plane.position())
    }
}
