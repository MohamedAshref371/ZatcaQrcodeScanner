package com.poetscorner.invoiceqr.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.data.ScannerSettings
import java.io.Closeable

/** Blocking operations: call from the camera executor or Dispatchers.IO, never the UI thread. */
interface QrDecoder : Closeable {
    fun read(image: ImageProxy, settings: ScannerSettings): String?
    fun read(bitmap: Bitmap, settings: ScannerSettings): String?
    fun readAll(bitmap: Bitmap, settings: ScannerSettings): List<String>
}

fun ScanEngine.decoder(): QrDecoder = when (this) {
    ScanEngine.ZXING -> ZxingDecoder()
    ScanEngine.ML_KIT -> MlKitDecoder()
}

private class ZxingDecoder : QrDecoder {
    override fun read(image: ImageProxy, settings: ScannerSettings) =
        settings.reader().read(image).firstOrNull { it.error == null && it.text != null }?.text

    override fun read(bitmap: Bitmap, settings: ScannerSettings) =
        settings.reader().read(bitmap).firstOrNull { it.error == null && it.text != null }?.text

    override fun readAll(bitmap: Bitmap, settings: ScannerSettings): List<String> =
        settings.reader(maxSymbols = 16).read(bitmap).mapNotNull { result ->
            result.text?.takeIf { result.error == null }
        }

    override fun close() = Unit
}

private class MlKitDecoder : QrDecoder {
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())

    override fun read(image: ImageProxy, settings: ScannerSettings): String? {
        val crop = image.cropRect
        val plane = image.planes[0]
        val frame = croppedNv21(plane.buffer, plane.rowStride, plane.pixelStride,
            crop.left, crop.top, crop.width(), crop.height())
        // fromMediaImage would scan the whole frame; copying the crop enforces the same ROI as ZXing.
        return read(InputImage.fromByteArray(frame.bytes, frame.width, frame.height,
            image.imageInfo.rotationDegrees, InputImage.IMAGE_FORMAT_NV21))
    }

    override fun read(bitmap: Bitmap, settings: ScannerSettings) = read(InputImage.fromBitmap(bitmap, 0))

    override fun readAll(bitmap: Bitmap, settings: ScannerSettings): List<String> =
        Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0))).mapNotNull { it.rawValue }

    private fun read(image: InputImage): String? =
        Tasks.await(scanner.process(image)).firstOrNull { it.rawValue != null }?.rawValue

    override fun close() = scanner.close()
}
