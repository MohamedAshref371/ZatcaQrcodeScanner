package com.poetscorner.invoiceqr.shared

/** Bounded, in-memory fusion of aligned grayscale views of one QR symbol. */
class MultiFrameConsensus(private val side: Int = 512) {
    private data class Frame(val pixels: ByteArray, val at: Long)
    private val frames = ArrayDeque<Frame>(4)
    private var lastAttemptAt: Long? = null

    fun reset() {
        frames.clear()
        lastAttemptAt = null
    }

    /** A valid QR decode is checked by the caller before delivery. */
    fun offer(pixels: ByteArray, at: Long, decode: (ByteArray) -> String?): String? {
        require(pixels.size == side * side)
        if (frames.isNotEmpty() && (at - frames.last().at > 1200L || at - frames.first().at > 4000L)) reset()
        if (frames.isNotEmpty() && agreement(frames.last().pixels, pixels) < 0.76) reset()
        frames.addLast(Frame(pixels, at))
        if (frames.size < 2) return null
        if (lastAttemptAt?.let { at - it < 250L } == true) {
            if (frames.size == 4) frames.removeFirst()
            return null
        }
        lastAttemptAt = at
        val result = decode(adaptiveThreshold(average(frames.map { it.pixels })))
        if (result != null) { reset(); return result }
        if (frames.size == 4) frames.removeFirst()
        return null
    }

    /** Ignore QR finder corners and compare the data-bearing interior at sampled locations. */
    private fun agreement(a: ByteArray, b: ByteArray): Double {
        var equal = 0
        var total = 0
        val start = side / 8
        val end = side - start
        val step = (side / 64).coerceAtLeast(1)
        for (y in start until end step step) for (x in start until end step step) {
            val index = y * side + x
            if (((a[index].toInt() and 255) < 128) == ((b[index].toInt() and 255) < 128)) equal++
            total++
        }
        return equal.toDouble() / total
    }

    private fun average(images: List<ByteArray>): ByteArray {
        val result = ByteArray(side * side)
        for (i in result.indices) {
            var sum = 0
            for (image in images) sum += image[i].toInt() and 255
            result[i] = (sum / images.size).toByte()
        }
        return result
    }

    /** Three small box blurs approximate Gaussian local binarization without a native image library. */
    private fun adaptiveThreshold(gray: ByteArray): ByteArray {
        val radius = (side / 46).coerceAtLeast(3)
        var blurred = IntArray(gray.size) { gray[it].toInt() and 255 }
        repeat(3) { blurred = boxBlur(blurred, radius) }
        val result = ByteArray(side * side)
        for (i in result.indices) {
            result[i] = if ((gray[i].toInt() and 255) < blurred[i] - 6) 0 else 255.toByte()
        }
        return result
    }

    private fun boxBlur(source: IntArray, radius: Int): IntArray {
        val width = radius * 2 + 1
        val horizontal = IntArray(source.size)
        for (y in 0 until side) {
            val row = y * side
            var sum = source[row] * (radius + 1)
            for (x in 1..radius) sum += source[row + x]
            for (x in 0 until side) {
                horizontal[row + x] = sum / width
                sum += source[row + (x + radius + 1).coerceAtMost(side - 1)] -
                    source[row + (x - radius).coerceAtLeast(0)]
            }
        }
        val result = IntArray(source.size)
        for (x in 0 until side) {
            var sum = horizontal[x] * (radius + 1)
            for (y in 1..radius) sum += horizontal[y * side + x]
            for (y in 0 until side) {
                result[y * side + x] = sum / width
                sum += horizontal[(y + radius + 1).coerceAtMost(side - 1) * side + x] -
                    horizontal[(y - radius).coerceAtLeast(0) * side + x]
            }
        }
        return result
    }
}

/** Stretch the 5th to 95th percentile range before comparing views from different exposures. */
fun normalizeQrLuma(pixels: ByteArray): ByteArray? {
    if (pixels.isEmpty()) return null
    val histogram = IntArray(256)
    for (pixel in pixels) histogram[pixel.toInt() and 255]++

    fun percentile(fraction: Double): Int {
        val target = (pixels.size * fraction).toInt()
        var count = 0
        for (level in histogram.indices) {
            count += histogram[level]
            if (count >= target) return level
        }
        return 255
    }

    val low = percentile(0.05)
    val high = percentile(0.95)
    if (high - low < 50) return null
    return ByteArray(pixels.size) { index ->
        ((((pixels[index].toInt() and 255) - low) * 255) / (high - low)).coerceIn(0, 255).toByte()
    }
}
