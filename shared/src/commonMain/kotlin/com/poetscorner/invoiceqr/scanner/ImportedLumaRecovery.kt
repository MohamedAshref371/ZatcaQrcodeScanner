package com.poetscorner.invoiceqr.scanner

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Attempt the same imported-photo passes as the Android bitmap reader. */
internal fun QrReader.readImportedImage(source: LumaImage, options: QrReadOptions): String? {
    fun decode(image: LumaImage) = read(image, options).firstOrNull { it.error == null && it.text != null }?.text
    decode(source)?.let { return it }

    val scale = minOf(1.0, 2400.0 / maxOf(source.width, source.height))
    val working = if (scale < 1) {
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val pixels = ByteArray(width * height) { index ->
            source.sample((index % width + 0.5) / scale, (index / width + 0.5) / scale).toByte()
        }
        LumaImage(pixels, width, height)
    } else source
    if (working !== source) decode(working)?.let { return it }

    val corrected = working.stretchContrast()
    if (corrected != null) decode(corrected)?.let { return it }
    val angles = intArrayOf(8, -8, 16, -16)
    for (angle in angles) decode(working.rotateOnWhite(angle))?.let { return it }
    if (corrected != null) {
        for (angle in angles) decode(corrected.rotateOnWhite(angle))?.let { return it }
    }
    return null
}

private fun LumaImage.stretchContrast(): LumaImage? {
    val histogram = IntArray(256)
    val step = maxOf(1, maxOf(width, height) / 256)
    var count = 0
    for (y in 0 until height step step) for (x in 0 until width step step) {
        histogram[pixels[y * width + x].toInt() and 255]++
        count++
    }
    fun percentile(fraction: Double): Int {
        val target = (count * fraction).toInt()
        var total = 0
        return histogram.indices.first { total += histogram[it]; total > target }
    }
    val shadows = percentile(0.05)
    val highlights = percentile(0.95)
    if (highlights >= 180 && highlights - shadows >= 110) return null
    val gain = (220.0 / (highlights - shadows).coerceAtLeast(1)).coerceIn(1.0, 3.0)
    val stretched = ByteArray(pixels.size) { i ->
        (20 + ((pixels[i].toInt() and 255) - shadows) * gain).roundToInt().coerceIn(0, 255).toByte()
    }
    return LumaImage(stretched, width, height)
}

private fun LumaImage.rotateOnWhite(degrees: Int): LumaImage {
    val angle = degrees * PI / 180
    val c = cos(angle)
    val s = sin(angle)
    val outWidth = ceil(width * abs(c) + height * abs(s)).toInt()
    val outHeight = ceil(width * abs(s) + height * abs(c)).toInt()
    val rotated = ByteArray(outWidth * outHeight) { index ->
        val x = index % outWidth + 0.5 - outWidth / 2.0
        val y = index / outWidth + 0.5 - outHeight / 2.0
        sample(c * x + s * y + width / 2.0, -s * x + c * y + height / 2.0).toByte()
    }
    return LumaImage(rotated, outWidth, outHeight)
}
