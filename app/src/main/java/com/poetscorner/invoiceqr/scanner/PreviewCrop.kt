package com.poetscorner.invoiceqr.scanner

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor

/** Map a visible region to the analysis buffer, even with different stream aspect ratios. */
internal fun previewCropInBuffer(
    viewToSensor: Matrix,
    sensorToBuffer: Matrix,
    viewRegion: RectF,
    bufferBounds: Rect
): Rect? {
    val region = RectF(viewRegion)
    viewToSensor.mapRect(region)
    sensorToBuffer.mapRect(region)
    val crop = Rect(ceil(region.left).toInt(), ceil(region.top).toInt(),
        floor(region.right).toInt(), floor(region.bottom).toInt())
    return crop.takeIf { it.intersect(bufferBounds) && it.width() >= 2 && it.height() >= 2 }
}
