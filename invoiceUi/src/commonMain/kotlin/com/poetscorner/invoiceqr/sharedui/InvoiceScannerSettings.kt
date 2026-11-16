package com.poetscorner.invoiceqr.sharedui

enum class InvoiceQrEngine { ZXING, ML_KIT }

data class InvoiceZxingSettings(
    val harder: Boolean = true,
    val rotate: Boolean = true,
    val inverted: Boolean = false,
    val binarizer: String = "LOCAL_AVERAGE",
    val multiFrameRecovery: Boolean = false,
)

interface InvoiceScannerSettings {
    fun selectedEngine(): InvoiceQrEngine
    fun mlKitAvailable(): Boolean
    fun setEngine(engine: InvoiceQrEngine)
    fun cropEnabled(): Boolean
    fun setCropEnabled(enabled: Boolean)
    fun cameraAvailable(): Boolean
    fun torchAvailable(): Boolean
    fun zxingSettings(): InvoiceZxingSettings
    fun setZxingSettings(settings: InvoiceZxingSettings)
    fun scanSpeed(): String
    fun setScanSpeed(speed: String)
    fun appearanceMode(): String
    fun setAppearanceMode(mode: String)
    fun appearanceColor(): String
    fun setAppearanceColor(color: String)
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
    val zxing: InvoiceZxingSettings,
    val intervalMillis: Int,
)
