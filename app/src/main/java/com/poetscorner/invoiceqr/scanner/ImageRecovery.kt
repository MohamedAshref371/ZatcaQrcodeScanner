package com.poetscorner.invoiceqr.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.poetscorner.invoiceqr.data.ScannerSettings
import kotlin.math.roundToInt

private const val MAX_RECOVERY_EDGE = 2400
private val TILT_ANGLES = floatArrayOf(8f, -8f, 16f, -16f)

/** Extra attempts for imported photos only. The original image always gets the first attempt. */
fun QrDecoder.readImportedImage(bitmap: Bitmap, settings: ScannerSettings): String? {
    read(bitmap, settings)?.let { return it }

    val scale = minOf(1f, MAX_RECOVERY_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
    val working = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1), true) else bitmap
    try {
        if (working !== bitmap) read(working, settings)?.let { return it }

        val levels = sampledLevels(working)
        val corrected = if (levels != null) contrastCopy(working, levels.first, levels.second) else null
        try {
            if (corrected != null) read(corrected, settings)?.let { return it }
            // Small in-plane rotations help when a photographed code is tilted.
            for (angle in TILT_ANGLES) {
                val rotated = rotateOnWhite(working, angle)
                try { read(rotated, settings)?.let { return it } }
                finally { rotated.recycle() }
            }
            if (corrected != null) for (angle in TILT_ANGLES) {
                val rotated = rotateOnWhite(corrected, angle)
                try { read(rotated, settings)?.let { return it } }
                finally { rotated.recycle() }
            }
            return null
        } finally {
            corrected?.recycle()
        }
    } finally {
        if (working !== bitmap) working.recycle()
    }
}

/** Return shadows and highlights only when the image is dark or has weak contrast. */
private fun sampledLevels(bitmap: Bitmap): Pair<Int, Int>? {
    val histogram = IntArray(256)
    val row = IntArray(bitmap.width)
    val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / 256)
    var count = 0
    for (y in 0 until bitmap.height step step) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in 0 until bitmap.width step step) {
            val color = row[x]
            val luma = (Color.red(color) * 77 + Color.green(color) * 150 +
                Color.blue(color) * 29) shr 8
            histogram[luma]++
            count++
        }
    }
    fun percentile(fraction: Double): Int {
        val target = (count * fraction).toInt()
        var seen = 0
        for (level in histogram.indices) {
            seen += histogram[level]
            if (seen > target) return level
        }
        return 255
    }
    val shadow = percentile(0.05)
    val highlight = percentile(0.95)
    return if (highlight < 180 || highlight - shadow < 110) shadow to highlight else null
}

private fun contrastCopy(source: Bitmap, shadow: Int, highlight: Int): Bitmap {
    val gain = (220f / (highlight - shadow).coerceAtLeast(1)).coerceIn(1f, 3f)
    val offset = 20f - shadow * gain
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            gain, 0f, 0f, 0f, offset,
            0f, gain, 0f, 0f, offset,
            0f, 0f, gain, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawBitmap(source, 0f, 0f, paint)
    }
}

private fun rotateOnWhite(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { setRotate(degrees, source.width / 2f, source.height / 2f) }
    val bounds = android.graphics.RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
    matrix.mapRect(bounds)
    val result = Bitmap.createBitmap(bounds.width().roundToInt(), bounds.height().roundToInt(),
        Bitmap.Config.ARGB_8888)
    result.eraseColor(Color.WHITE)
    matrix.postTranslate(-bounds.left, -bounds.top)
    Canvas(result).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return result
}
