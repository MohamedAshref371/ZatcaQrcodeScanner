package com.poetscorner.invoiceqr.scanner

/** Packed 8-bit luminance, one byte per pixel, in row-major order. */
class LumaImage(val pixels: ByteArray, val width: Int, val height: Int, val rotationDegrees: Int = 0) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        require(rotationDegrees in 0..270 && rotationDegrees % 90 == 0)
    }
}

enum class QrBinarizer { LOCAL_AVERAGE, GLOBAL_HISTOGRAM, FIXED_THRESHOLD, BOOL_CAST }

data class QrReadOptions(
    val tryHarder: Boolean = false,
    val tryDenoise: Boolean = false,
    val tryRotate: Boolean = true,
    val tryInvert: Boolean = false,
    val tryDownscale: Boolean = true,
    val binarizer: QrBinarizer = QrBinarizer.LOCAL_AVERAGE,
    val maxNumberOfSymbols: Int = 1,
    val returnErrors: Boolean = false,
)

data class QrPoint(val x: Int, val y: Int)

/** Corners in crop-local coordinates after applying the image rotation. */
data class QrPosition(
    val topLeft: QrPoint,
    val topRight: QrPoint,
    val bottomRight: QrPoint,
    val bottomLeft: QrPoint,
)

enum class QrFormat { QR_CODE, MODEL_1, MODEL_2, OTHER }
enum class QrReadError { FORMAT, CHECKSUM, OTHER }

data class QrSymbol(
    val text: String?,
    val format: QrFormat,
    val position: QrPosition,
    val error: QrReadError?,
)

fun interface QrReader {
    /** Blocking. The caller retains ownership of the pixel buffer. */
    fun read(image: LumaImage, options: QrReadOptions): List<QrSymbol>
}
