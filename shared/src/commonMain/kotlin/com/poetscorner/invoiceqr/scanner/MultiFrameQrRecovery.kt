package com.poetscorner.invoiceqr.scanner

/** In-memory recovery of a single QR across multiple camera frames. */
class MultiFrameQrRecovery {
    private companion object {
        const val SIDE = 512
        const val BORDER = 32
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

    /** The crop and detected corners use the decoder's coordinate system. */
    fun offer(width: Int, height: Int, rotation: Int, position: QrPosition?, now: Long,
        allowGuideFallback: Boolean, pixel: (x: Int, y: Int) -> Byte,
        decode: (LumaImage) -> String?): String? {
        val dimensions = if (rotation % 180 == 0) width to height else height to width
        if (lastDimensions != null && lastDimensions != dimensions) reset()
        val recentPosition = lastPosition != null && now - lastPositionAt <= 1800L
        if (position == null && !recentPosition && !allowGuideFallback) {
            reset()
            return null
        }
        val (source, step) = sampleUprightLuma(width, height, rotation, pixel)
        if (position == null && !recentPosition && allowGuideFallback && !usingGuide) {
            reset()
            usingGuide = true
        }
        lastDimensions = dimensions
        if (position != null && !usingGuide) {
            lastPosition = position
            lastPositionAt = now
        }
        val right = source.width * step - 1
        val bottom = source.height * step - 1
        val guide = QrPosition(QrPoint(0, 0), QrPoint(right, 0), QrPoint(right, bottom), QrPoint(0, bottom))
        val corners = if (usingGuide) guide else lastPosition ?: return null
        val aligned = alignQrLuma(source, corners, step, SIDE) ?: run { reset(); return null }
        return consensus.offer(aligned, now) { binary ->
            val edge = SIDE + BORDER * 2
            val bordered = ByteArray(edge * edge) { 255.toByte() }
            for (row in 0 until SIDE) {
                binary.copyInto(bordered, (row + BORDER) * edge + BORDER, row * SIDE, (row + 1) * SIDE)
            }
            decode(LumaImage(bordered, edge, edge))
        }
    }
}
