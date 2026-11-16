package com.poetscorner.invoiceqr.shared

data class InvoiceScanGuide(val left: Int, val top: Int, val size: Int)

/** Largest square in the preview area left unobstructed by the controls. */
fun invoiceScanGuide(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): InvoiceScanGuide {
    require(width > 0 && height > 0)
    val x = left.coerceIn(0, width - 1)
    val y = top.coerceIn(0, height - 1)
    val availableWidth = (width - right - x).coerceIn(1, width - x)
    val availableHeight = (height - bottom - y).coerceIn(1, height - y)
    val side = minOf(availableWidth, availableHeight, (minOf(width, height) * 0.8f).toInt().coerceAtLeast(1))
    return InvoiceScanGuide(x + (availableWidth - side) / 2, y + (availableHeight - side) / 2, side)
}
