package com.poetscorner.invoiceqr.scanner

data class ScanWindow(val left: Int, val top: Int, val size: Int)

/** Shared geometry for the visible guide and the actual crop passed to the QR reader. */
fun scanWindow(width: Int, height: Int): ScanWindow {
    require(width > 0 && height > 0)
    val side = (minOf(width, height) * 0.8f).toInt().coerceAtLeast(1)
    return ScanWindow((width - side) / 2, (height - side) / 2, side)
}
