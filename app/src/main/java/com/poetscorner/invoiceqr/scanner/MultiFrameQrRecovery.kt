package com.poetscorner.invoiceqr.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import kotlin.math.max

/** Camera-only recovery. The image proxy is copied before CameraX closes it; no frame reaches storage. */
internal class MultiFrameQrRecovery {
    private companion object {
        const val SIDE = 512
        const val BORDER = 32
        const val MAX_INPUT_EDGE = 1000
    }

    /** ZXing can report the same damaged QR more than once with slightly different corners. */
    fun samePosition(a: QrPosition, b: QrPosition): Boolean {
        fun bounds(p: QrPosition): FloatArray {
            val xs = intArrayOf(p.topLeft.x, p.topRight.x, p.bottomRight.x, p.bottomLeft.x)
            val ys = intArrayOf(p.topLeft.y, p.topRight.y, p.bottomRight.y, p.bottomLeft.y)
            return floatArrayOf(xs.min().toFloat(), ys.min().toFloat(), xs.max().toFloat(), ys.max().toFloat())
        }
        val x = bounds(a)
        val y = bounds(b)
        val areaX = (x[2] - x[0]) * (x[3] - x[1])
        val areaY = (y[2] - y[0]) * (y[3] - y[1])
        val common = (minOf(x[2], y[2]) - maxOf(x[0], y[0])).coerceAtLeast(0f) *
            (minOf(x[3], y[3]) - maxOf(x[1], y[1])).coerceAtLeast(0f)
        return minOf(areaX, areaY) > 0f && common / minOf(areaX, areaY) > 0.8f &&
            maxOf(areaX, areaY) / minOf(areaX, areaY) < 1.5f
    }

    private val consensus = MultiFrameConsensus(SIDE)
    private var lastPosition: QrPosition? = null
    private var lastPositionAt = 0L
    private var lastDimensions: Pair<Int, Int>? = null
    private var usingGuide = false

    fun reset() {
        consensus.reset()
        lastPosition = null
        lastDimensions = null
        usingGuide = false
    }

    /** A tight scan guide can seed recovery when ZXing cannot locate a damaged QR at all. */
    fun offer(image: ImageProxy, position: QrPosition?, now: Long,
        allowGuideFallback: Boolean,
        decode: (Bitmap) -> String?): String? {
        val recentPosition = lastPosition != null && now - lastPositionAt <= 1800L
        if (position == null && !recentPosition && !allowGuideFallback) {
            reset()
            return null
        }
        val (bitmap, step) = copyUprightLuma(image)
        try {
            val dimensions = bitmap.width to bitmap.height
            if (lastDimensions != null && lastDimensions != dimensions) reset()
            if (position == null && !recentPosition && allowGuideFallback && !usingGuide) {
                reset()
                usingGuide = true
            }
            lastDimensions = dimensions
            if (position != null && !usingGuide) {
                lastPosition = position
                lastPositionAt = now
            }
            val qr = if (usingGuide) guidePosition(bitmap, step) else lastPosition ?: return null
            val aligned = align(bitmap, qr, step) ?: run { reset(); return null }
            return consensus.offer(aligned, now) { binary ->
                val candidate = binaryBitmap(binary)
                try { decode(candidate) } finally { candidate.recycle() }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun guidePosition(bitmap: Bitmap, step: Int): QrPosition {
        val right = bitmap.width * step - 1
        val bottom = bitmap.height * step - 1
        return QrPosition(QrPoint(0, 0), QrPoint(right, 0),
            QrPoint(right, bottom), QrPoint(0, bottom))
    }

    private fun copyUprightLuma(image: ImageProxy): Pair<Bitmap, Int> {
        val crop = image.cropRect
        val step = max(1, (max(crop.width(), crop.height()) + MAX_INPUT_EDGE - 1) / MAX_INPUT_EDGE)
        val width = (crop.width() + step - 1) / step
        val height = (crop.height() + step - 1) / step
        val rotation = image.imageInfo.rotationDegrees
        require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270)
        val outWidth = if (rotation % 180 == 0) width else height
        val outHeight = if (rotation % 180 == 0) height else width
        val colors = IntArray(outWidth * outHeight)
        val plane = image.planes[0]
        val buffer = plane.buffer
        for (y in 0 until height) for (x in 0 until width) {
            val value = buffer.get((crop.top + y * step) * plane.rowStride +
                (crop.left + x * step) * plane.pixelStride).toInt() and 255
            val dx: Int
            val dy: Int
            when (rotation) {
                90 -> { dx = height - 1 - y; dy = x }
                180 -> { dx = width - 1 - x; dy = height - 1 - y }
                270 -> { dx = y; dy = width - 1 - x }
                else -> { dx = x; dy = y }
            }
            colors[dy * outWidth + dx] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also {
            it.setPixels(colors, 0, outWidth, 0, 0, outWidth, outHeight)
        } to step
    }

    private fun align(source: Bitmap, position: QrPosition, step: Int): ByteArray? {
        val points = listOf(position.topLeft, position.topRight, position.bottomRight, position.bottomLeft)
        val src = FloatArray(8)
        points.forEachIndexed { index, point ->
            src[index * 2] = point.x.toFloat() / step
            src[index * 2 + 1] = point.y.toFloat() / step
        }
        if (src.indices.step(2).any { src[it] < -4 || src[it] > source.width + 4 ||
                src[it + 1] < -4 || src[it + 1] > source.height + 4 }) return null
        val top = kotlin.math.hypot(src[2] - src[0], src[3] - src[1])
        val left = kotlin.math.hypot(src[6] - src[0], src[7] - src[1])
        if (top < 100 || left < 100) return null
        val dst = floatArrayOf(0f, 0f, SIDE - 1f, 0f, SIDE - 1f, SIDE - 1f, 0f, SIDE - 1f)
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return null
        val warped = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        try {
            warped.eraseColor(Color.WHITE)
            Canvas(warped).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            val colors = IntArray(SIDE * SIDE)
            warped.getPixels(colors, 0, SIDE, 0, 0, SIDE, SIDE)
            val histogram = IntArray(256)
            for (color in colors) histogram[Color.red(color)]++
            fun percentile(fraction: Double): Int {
                val target = (colors.size * fraction).toInt()
                var count = 0
                for (level in histogram.indices) {
                    count += histogram[level]
                    if (count > target) return level
                }
                return 255
            }
            val low = percentile(0.05)
            val high = percentile(0.95)
            if (high - low < 50) return null
            return ByteArray(colors.size) { index ->
                (((Color.red(colors[index]) - low) * 255) / (high - low)).coerceIn(0, 255).toByte()
            }
        } finally {
            warped.recycle()
        }
    }

    private fun binaryBitmap(pixels: ByteArray): Bitmap {
        val edge = SIDE + BORDER * 2
        val colors = IntArray(edge * edge) { Color.WHITE }
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            if ((pixels[y * SIDE + x].toInt() and 255) < 128)
                colors[(y + BORDER) * edge + x + BORDER] = Color.BLACK
        }
        return Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888).also {
            it.setPixels(colors, 0, edge, 0, 0, edge, edge)
        }
    }
}
