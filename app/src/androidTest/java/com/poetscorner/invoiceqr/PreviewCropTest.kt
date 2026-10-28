package com.poetscorner.invoiceqr

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.scanner.previewCropInBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewCropTest {
    @Test fun offCentreGuideMapsBackToTheSameSensorRegionAtEveryRotation() {
        val sensorGuide = RectF(600f, 200f, 900f, 500f)
        val sensorToBuffer = Matrix().apply { setScale(0.5f, 0.5f) }
        for (rotation in listOf(0f, 90f, 180f, 270f)) {
            val sensorToView = Matrix().apply {
                setRotate(rotation, 600f, 400f)
                postScale(0.75f, 0.75f)
                postTranslate(30f, 50f)
            }
            val viewGuide = RectF(sensorGuide)
            sensorToView.mapRect(viewGuide)
            val inverse = Matrix()
            assertTrue(sensorToView.invert(inverse))
            val crop = requireNotNull(previewCropInBuffer(inverse, sensorToBuffer, viewGuide, Rect(0, 0, 600, 400)))
            // Inward rounding can remove one pixel at each edge.
            assertEquals(300f, crop.left.toFloat(), 1f)
            assertEquals(100f, crop.top.toFloat(), 1f)
            assertEquals(450f, crop.right.toFloat(), 1f)
            assertEquals(250f, crop.bottom.toFloat(), 1f)
        }
    }

    @Test fun fullPreviewMapsToVisibleBufferCropAndStaysInsideBounds() {
        val viewToSensor = Matrix().apply { setScale(2f, 2f); postTranslate(100f, 0f) }
        val sensorToBuffer = Matrix().apply { setScale(0.5f, 0.5f) }
        assertEquals(Rect(50, 0, 450, 300), previewCropInBuffer(viewToSensor, sensorToBuffer,
            RectF(0f, 0f, 400f, 300f), Rect(0, 0, 500, 300)))
        assertNull(previewCropInBuffer(Matrix(), Matrix(), RectF(600f, 0f, 800f, 200f), Rect(0, 0, 500, 300)))
    }
}
