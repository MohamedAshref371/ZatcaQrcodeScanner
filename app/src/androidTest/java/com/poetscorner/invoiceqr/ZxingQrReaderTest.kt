package com.poetscorner.invoiceqr

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.scanner.AndroidZxingQrReader
import com.poetscorner.invoiceqr.scanner.LumaImage
import com.poetscorner.invoiceqr.scanner.QrReadOptions
import com.poetscorner.invoiceqr.scanner.QrReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZxingQrReaderTest {
    @Test fun packedLumaAndBitmapDecodeTheSameSymbol() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("play-services-qr.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            val colors = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val luma = ByteArray(colors.size) { Color.red(colors[it]).toByte() }
            val options = QrReadOptions()
            val fromBitmap = AndroidZxingQrReader.read(bitmap, options).single()
            val reader: QrReader = AndroidZxingQrReader
            val fromLuma = reader.read(LumaImage(luma, bitmap.width, bitmap.height), options).single()
            assertEquals("invoiceqr-play-services-integration", fromLuma.text)
            assertEquals(fromBitmap, fromLuma)
        } finally {
            bitmap.recycle()
        }
    }
}
