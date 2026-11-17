package com.poetscorner.invoiceqr.ui

// Multi-window can report a compact width even when hosted on a tablet.
fun portraitLockApplies(enabled: Boolean, smallestWidthDp: Int, multiWindow: Boolean): Boolean =
    enabled && smallestWidthDp in 1 until 600 && !multiWindow
