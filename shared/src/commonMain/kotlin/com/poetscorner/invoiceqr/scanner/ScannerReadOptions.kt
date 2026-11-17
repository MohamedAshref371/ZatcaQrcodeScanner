package com.poetscorner.invoiceqr.scanner

import com.poetscorner.invoiceqr.data.ScannerSettings

fun ScannerSettings.qrReadOptions(maxSymbols: Int = 1, returnErrors: Boolean = false) = QrReadOptions(
    tryHarder = harder,
    tryDenoise = denoise,
    tryRotate = rotate,
    tryInvert = inverted,
    maxNumberOfSymbols = maxSymbols,
    returnErrors = returnErrors,
    binarizer = QrBinarizer.entries.firstOrNull { it.name == binarizer } ?: QrBinarizer.LOCAL_AVERAGE,
)
