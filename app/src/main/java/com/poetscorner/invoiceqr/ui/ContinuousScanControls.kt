package com.poetscorner.invoiceqr.ui

enum class ContinuousScanStatus { SAVED, REVIEW, DUPLICATE, SAVE_FAILED }
data class ContinuousScanNotice(val result: ScanResult, val status: ContinuousScanStatus)
data class ContinuousScanState(val enabled: Boolean = false, val added: Int = 0,
    val notice: ContinuousScanNotice? = null)
