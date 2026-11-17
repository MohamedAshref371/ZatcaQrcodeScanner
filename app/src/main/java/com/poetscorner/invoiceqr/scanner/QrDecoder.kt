package com.poetscorner.invoiceqr.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.poetscorner.invoiceqr.data.ScannerSettings
import java.io.Closeable

/** Blocking operations: call from the camera executor or Dispatchers.IO, never the UI thread. */
interface QrDecoder : Closeable {
    fun read(image: ImageProxy, settings: ScannerSettings): String?
    fun read(bitmap: Bitmap, settings: ScannerSettings): String?
    fun readAll(bitmap: Bitmap, settings: ScannerSettings): List<String>
}

internal class ZxingDecoder : QrDecoder {
    override fun read(image: ImageProxy, settings: ScannerSettings) =
        AndroidZxingQrReader.read(image, settings.qrReadOptions()).firstOrNull { it.error == null && it.text != null }?.text

    override fun read(bitmap: Bitmap, settings: ScannerSettings) =
        AndroidZxingQrReader.read(bitmap, settings.qrReadOptions()).firstOrNull { it.error == null && it.text != null }?.text

    override fun readAll(bitmap: Bitmap, settings: ScannerSettings): List<String> =
        AndroidZxingQrReader.read(bitmap, settings.qrReadOptions(maxSymbols = 0xff)).mapNotNull { result ->
            result.text?.takeIf { result.error == null }
        }

    override fun close() = Unit
}
