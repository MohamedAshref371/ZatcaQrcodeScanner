package com.poetscorner.invoiceqr.sharedui

enum class InvoiceQrEngine { ZXING, ML_KIT }

interface InvoiceScannerSettings {
    fun selectedEngine(): InvoiceQrEngine
    fun mlKitAvailable(): Boolean
    fun setEngine(engine: InvoiceQrEngine)
    fun cropEnabled(): Boolean
    fun setCropEnabled(enabled: Boolean)
    fun cameraAvailable(): Boolean
    fun torchAvailable(): Boolean
}

data class InvoiceCameraConfiguration(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cropEnabled: Boolean,
    val zoom: Float,
    val torch: Boolean,
    val engine: InvoiceQrEngine,
)
