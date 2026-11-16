package com.poetscorner.invoiceqr.scanner

import com.poetscorner.invoiceqr.shared.invoiceScanGuide

data class ScanWindow(val left: Int, val top: Int, val size: Int)

/** Shared geometry for the visible guide and the actual crop passed to the QR reader. */
fun scanWindow(width: Int, height: Int, sideFraction: Float = 0.8f): ScanWindow {
    require(width > 0 && height > 0)
    require(sideFraction > 0f && sideFraction <= 1f)
    val side = (minOf(width, height) * sideFraction).toInt().coerceAtLeast(1)
    return ScanWindow((width - side) / 2, (height - side) / 2, side)
}

/** Largest guide in the unobstructed area, rather than shrinking around the screen centre. */
fun scanGuideWindow(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): ScanWindow {
    val guide = invoiceScanGuide(width, height, left, top, right, bottom)
    return ScanWindow(guide.left, guide.top, guide.size)
}
