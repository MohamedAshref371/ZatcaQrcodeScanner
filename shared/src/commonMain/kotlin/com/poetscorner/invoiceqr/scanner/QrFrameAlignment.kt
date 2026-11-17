package com.poetscorner.invoiceqr.scanner

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MAX_INPUT_EDGE = 1000

internal fun sampleUprightLuma(width: Int, height: Int, rotation: Int,
    pixel: (Int, Int) -> Byte): Pair<LumaImage, Int> {
    require(width > 0 && height > 0 && rotation in 0..270 && rotation % 90 == 0)
    val step = (maxOf(width, height) - 1) / MAX_INPUT_EDGE + 1
    val columns = (width - 1) / step + 1
    val rows = (height - 1) / step + 1
    val outWidth = if (rotation % 180 == 0) columns else rows
    val outHeight = if (rotation % 180 == 0) rows else columns
    val result = ByteArray(outWidth * outHeight)
    for (y in 0 until rows) for (x in 0 until columns) {
        val index = when (rotation) {
            90 -> x * outWidth + rows - 1 - y
            180 -> (rows - 1 - y) * outWidth + columns - 1 - x
            270 -> (columns - 1 - x) * outWidth + y
            else -> y * outWidth + x
        }
        result[index] = pixel(x * step, y * step)
    }
    return LumaImage(result, outWidth, outHeight) to step
}

internal fun alignQrLuma(source: LumaImage, position: QrPosition, step: Int, side: Int): ByteArray? {
    val points = listOf(position.topLeft, position.topRight, position.bottomRight, position.bottomLeft)
    val x = points.map { it.x.toDouble() / step }
    val y = points.map { it.y.toDouble() / step }
    if (x.any { it < -4 || it > source.width + 4 } || y.any { it < -4 || it > source.height + 4 }) return null
    if (hypot(x[1] - x[0], y[1] - y[0]) < 100 || hypot(x[3] - x[0], y[3] - y[0]) < 100) return null

    // Inverse projective transform from the aligned square to the four observed corners.
    val dx1 = x[1] - x[2]
    val dx2 = x[3] - x[2]
    val dy1 = y[1] - y[2]
    val dy2 = y[3] - y[2]
    val dx3 = x[0] - x[1] + x[2] - x[3]
    val dy3 = y[0] - y[1] + y[2] - y[3]
    val det = dx1 * dy2 - dx2 * dy1
    if (abs(det) < 1e-8) return null
    val g = (dx3 * dy2 - dx2 * dy3) / det
    val h = (dx1 * dy3 - dx3 * dy1) / det
    if (1.0 + g <= 0 || 1.0 + h <= 0 || 1.0 + g + h <= 0) return null
    val a = x[1] - x[0] + g * x[1]
    val b = x[3] - x[0] + h * x[3]
    val d = y[1] - y[0] + g * y[1]
    val e = y[3] - y[0] + h * y[3]
    val result = ByteArray(side * side)
    val histogram = IntArray(256)
    val span = side - 1.0
    for (row in 0 until side) {
        val v = (row + 0.5) / span
        for (col in 0 until side) {
            val u = (col + 0.5) / span
            val w = g * u + h * v + 1.0
            val sample = source.sample((a * u + b * v + x[0]) / w, (d * u + e * v + y[0]) / w)
            result[row * side + col] = sample.toByte()
            histogram[sample]++
        }
    }
    fun percentile(fraction: Double): Int {
        val target = (result.size * fraction).toInt()
        var count = 0
        return histogram.indices.first { count += histogram[it]; count > target }
    }
    val low = percentile(0.05)
    val high = percentile(0.95)
    if (high - low < 50) return null
    for (index in result.indices) {
        result[index] = ((((result[index].toInt() and 255) - low) * 255) / (high - low))
            .coerceIn(0, 255).toByte()
    }
    return result
}

internal fun LumaImage.sample(x: Double, y: Double): Int {
    if (x < 0 || x >= width || y < 0 || y >= height) return 255
    val sx = (x - 0.5).coerceIn(0.0, width - 1.0)
    val sy = (y - 0.5).coerceIn(0.0, height - 1.0)
    val left = floor(sx).toInt()
    val top = floor(sy).toInt()
    val right = (left + 1).coerceAtMost(width - 1)
    val bottom = (top + 1).coerceAtMost(height - 1)
    val fx = sx - left
    val fy = sy - top
    val v00 = pixels[top * width + left].toInt() and 255
    val v10 = pixels[top * width + right].toInt() and 255
    val v01 = pixels[bottom * width + left].toInt() and 255
    val v11 = pixels[bottom * width + right].toInt() and 255
    val upper = v00 + (v10 - v00) * fx
    val lower = v01 + (v11 - v01) * fx
    return (upper + (lower - upper) * fy).roundToInt()
}
