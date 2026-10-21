package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.ScanWindow
import com.poetscorner.invoiceqr.scanner.scanWindow
import org.junit.Assert.*
import org.junit.Test

class ScanWindowTest {
    @Test fun portraitAndLandscapeUseTheSameCenteredSquare() {
        assertEquals(ScanWindow(108, 528, 864), scanWindow(1080, 1920))
        assertEquals(ScanWindow(528, 108, 864), scanWindow(1920, 1080))
        assertEquals(ScanWindow(108, 108, 864), scanWindow(1080, 1080))
    }

    @Test fun differentPreviewAndAnalysisResolutionsDescribeTheSameSensorRegion() {
        val preview = scanWindow(1080, 1920)
        // Analysis buffers can be in sensor orientation, rotated 90 degrees from the preview.
        val analysis = scanWindow(1280, 720)
        assertEquals(preview.size / 1080f, analysis.size / 720f, 0.001f)
        assertEquals(preview.left / 1080f, analysis.top / 720f, 0.001f)
        assertEquals(preview.top / 1920f, analysis.left / 1280f, 0.001f)
    }

    @Test fun roundingKeepsTheCropInsideTheFrameAndCentered() {
        listOf(1 to 1, 721 to 1281, 1281 to 721, 403 to 917).forEach { (width, height) ->
            val window = scanWindow(width, height)
            assertTrue(window.size > 0)
            assertTrue(window.left >= 0 && window.top >= 0)
            assertTrue(window.left + window.size <= width)
            assertTrue(window.top + window.size <= height)
            assertTrue(kotlin.math.abs(window.left - (width - window.left - window.size)) <= 1)
            assertTrue(kotlin.math.abs(window.top - (height - window.top - window.size)) <= 1)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUnmeasuredPreview() { scanWindow(0, 1920) }
}
