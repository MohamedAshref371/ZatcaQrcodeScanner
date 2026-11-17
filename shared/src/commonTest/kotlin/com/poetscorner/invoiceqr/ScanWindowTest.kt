package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.ScanWindow
import com.poetscorner.invoiceqr.scanner.scanGuideWindow
import com.poetscorner.invoiceqr.scanner.scanWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScanWindowTest {
    @Test fun landscapeGuideUsesTheSpaceBesideControlsAndBetweenUnequalBars() {
        val window = scanGuideWindow(800, 360, left = 272, top = 68, right = 12, bottom = 92)
        assertEquals(200, window.size)
        assertEquals(ScanWindow(430, 68, 200), window)
        // The old screen-centred layout was limited by both the sidebar and bottom bar.
        val oldSide = minOf(360 - 2 * 104, (800 * 0.36f - 48).toInt())
        assertTrue(window.size > oldSide * 1.3f)
    }

    @Test fun arabicLayoutMirrorsTheGuideAwayFromTheControls() {
        val ltr = scanGuideWindow(800, 360, 272, 68, 12, 92)
        val rtl = scanGuideWindow(800, 360, 12, 68, 272, 92)
        assertEquals(ltr.size, rtl.size)
        assertEquals(800 - ltr.left - ltr.size, rtl.left)
        assertEquals(ltr.top, rtl.top)
    }

    @Test fun portraitGuideFitsBetweenHeaderAndFooterWithoutRequiringScreenSymmetry() {
        val window = scanGuideWindow(360, 800, 12, 240, 12, 170)
        assertEquals(288, window.size)
        assertTrue(window.top >= 240)
        assertTrue(window.top + window.size <= 630)
    }

    @Test fun guideRemainsInsideTinyOrObstructedWindows() {
        for ((w, h) in listOf(1 to 1, 240 to 160, 160 to 240)) {
            val window = scanGuideWindow(w, h, 280, 300, 280, 300)
            assertTrue(window.size >= 1)
            assertTrue(window.left >= 0 && window.top >= 0)
            assertTrue(window.left + window.size <= w)
            assertTrue(window.top + window.size <= h)
        }
    }

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

    @Test fun smallerGuideKeepsPreviewAndRotatedAnalysisAligned() {
        val preview = scanWindow(1080, 1920, 0.55f)
        val analysis = scanWindow(1280, 720, 0.55f)
        assertEquals(594, preview.size)
        assertEquals(preview.size / 1080f, analysis.size / 720f, 0.002f)
        assertEquals(preview.left / 1080f, analysis.top / 720f, 0.002f)
        assertEquals(preview.top / 1920f, analysis.left / 1280f, 0.002f)
    }

    @Test fun rejectsAnUnmeasuredPreview() {
        assertFailsWith<IllegalArgumentException> { scanWindow(0, 1920) }
    }
}
