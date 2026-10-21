package com.poetscorner.invoiceqr.scanner

import java.nio.ByteBuffer

internal data class GrayFrame(val bytes: ByteArray, val width: Int, val height: Int)

/** Copy only the scan window. Neutral chroma makes the cropped luminance a valid NV21 image. */
internal fun croppedNv21(y: ByteBuffer, rowStride: Int, pixelStride: Int,
    left: Int, top: Int, width: Int, height: Int): GrayFrame {
    // NV21 uses 2x2 chroma blocks; round inward so no pixels outside the guide are included.
    val w = width and -2
    val h = height and -2
    require(left >= 0 && top >= 0 && w > 0 && h > 0 && pixelStride > 0)
    require(rowStride >= (left + w - 1) * pixelStride + 1)
    val bytes = ByteArray(w * h * 3 / 2) { 128.toByte() }
    for (row in 0 until h) {
        val offset = (top + row) * rowStride + left * pixelStride
        for (column in 0 until w) bytes[row * w + column] = y.get(offset + column * pixelStride)
    }
    return GrayFrame(bytes, w, h)
}
