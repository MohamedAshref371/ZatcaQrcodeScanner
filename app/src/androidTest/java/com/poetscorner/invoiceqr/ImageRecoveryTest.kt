package com.poetscorner.invoiceqr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.QrDecoder
import com.poetscorner.invoiceqr.scanner.readImportedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageRecoveryTest {
    @Test fun darkImageGetsContrastRetryWithoutChangingOriginal() {
        val image = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(25, 25, 25))
        Canvas(image).drawRect(100f, 0f, 200f, 200f,
            Paint().apply { color = Color.rgb(90, 90, 90) })
        var attempts = 0
        val decoder = object : QrDecoder {
            override fun read(image: ImageProxy, settings: ScannerSettings): String? = null
            override fun read(bitmap: Bitmap, settings: ScannerSettings): String? {
                attempts++
                return if (Color.red(bitmap.getPixel(150, 100)) > 180) "recovered" else null
            }
            override fun readAll(bitmap: Bitmap, settings: ScannerSettings) = emptyList<String>()
            override fun close() = Unit
        }
        try {
            assertEquals("recovered", decoder.readImportedImage(image, ScannerSettings()))
            assertEquals(2, attempts)
            assertEquals(90, Color.red(image.getPixel(150, 100)))
            assertFalse(image.isRecycled)
        } finally {
            image.recycle()
        }
    }
}
