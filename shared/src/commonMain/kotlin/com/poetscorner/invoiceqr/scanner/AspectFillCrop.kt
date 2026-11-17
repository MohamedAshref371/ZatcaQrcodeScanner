package com.poetscorner.invoiceqr.scanner

import kotlin.math.ceil
import kotlin.math.floor

internal data class FrameCrop(val left: Int, val top: Int, val width: Int, val height: Int)

/** Convert a preview rectangle into the pixels of an upright, aspect-filled frame. */
internal fun aspectFillCrop(frameWidth: Int, frameHeight: Int, viewWidth: Int, viewHeight: Int,
    guide: ScanWindow?): FrameCrop? {
    if (minOf(frameWidth, frameHeight, viewWidth, viewHeight) <= 0) return null
    val scale = maxOf(viewWidth.toDouble() / frameWidth, viewHeight.toDouble() / frameHeight)
    val hiddenX = (frameWidth * scale - viewWidth) / 2
    val hiddenY = (frameHeight * scale - viewHeight) / 2
    val left = guide?.left ?: 0
    val top = guide?.top ?: 0
    val right = guide?.let { it.left + it.size } ?: viewWidth
    val bottom = guide?.let { it.top + it.size } ?: viewHeight
    val x0 = floor((left + hiddenX) / scale).toInt().coerceIn(0, frameWidth)
    val y0 = floor((top + hiddenY) / scale).toInt().coerceIn(0, frameHeight)
    val x1 = ceil((right + hiddenX) / scale).toInt().coerceIn(0, frameWidth)
    val y1 = ceil((bottom + hiddenY) / scale).toInt().coerceIn(0, frameHeight)
    return if (x1 > x0 && y1 > y0) FrameCrop(x0, y0, x1 - x0, y1 - y0) else null
}
