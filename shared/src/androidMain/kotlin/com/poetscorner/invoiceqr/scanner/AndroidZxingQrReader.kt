package com.poetscorner.invoiceqr.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

object AndroidZxingQrReader : QrReader {
    // Let ZXing read the cropped Y plane directly on the live camera path.
    fun read(image: ImageProxy, options: QrReadOptions): List<QrSymbol> =
        reader(options).read(image).map { it.toQrSymbol() }

    fun read(bitmap: Bitmap, options: QrReadOptions): List<QrSymbol> =
        reader(options).read(bitmap).map { it.toQrSymbol() }

    override fun read(image: LumaImage, options: QrReadOptions): List<QrSymbol> {
        // The Android wrapper exposes the raw Y buffer only through CameraX.
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(image.width)
            for (y in 0 until image.height) {
                for (x in row.indices) {
                    val luma = image.pixels[y * image.width + x].toInt() and 255
                    row[x] = (0xff shl 24) or (luma * 0x010101)
                }
                bitmap.setPixels(row, 0, image.width, 0, y, image.width, 1)
            }
            return reader(options).read(bitmap, rotation = image.rotationDegrees).map { it.toQrSymbol() }
        } finally {
            bitmap.recycle()
        }
    }

    private fun reader(options: QrReadOptions) = BarcodeReader(BarcodeReader.Options(
        formats = setOf(BarcodeReader.Format.QR_CODE),
        tryHarder = options.tryHarder,
        tryDenoise = options.tryDenoise,
        tryRotate = options.tryRotate,
        tryInvert = options.tryInvert,
        tryDownscale = options.tryDownscale,
        maxNumberOfSymbols = options.maxNumberOfSymbols,
        returnErrors = options.returnErrors,
        textMode = BarcodeReader.TextMode.PLAIN,
        binarizer = BarcodeReader.Binarizer.valueOf(options.binarizer.name),
    ))

    private fun BarcodeReader.Result.toQrSymbol() = QrSymbol(
        text = text,
        format = when (format) {
            BarcodeReader.Format.QR_CODE -> QrFormat.QR_CODE
            BarcodeReader.Format.QR_CODE_MODEL_1 -> QrFormat.MODEL_1
            BarcodeReader.Format.QR_CODE_MODEL_2 -> QrFormat.MODEL_2
            else -> QrFormat.OTHER
        },
        position = position.let {
            QrPosition(QrPoint(it.topLeft.x, it.topLeft.y), QrPoint(it.topRight.x, it.topRight.y),
                QrPoint(it.bottomRight.x, it.bottomRight.y), QrPoint(it.bottomLeft.x, it.bottomLeft.y))
        },
        error = error?.let {
            when (it.type) {
                BarcodeReader.ErrorType.FORMAT -> QrReadError.FORMAT
                BarcodeReader.ErrorType.CHECKSUM -> QrReadError.CHECKSUM
                else -> QrReadError.OTHER
            }
        },
    )
}
