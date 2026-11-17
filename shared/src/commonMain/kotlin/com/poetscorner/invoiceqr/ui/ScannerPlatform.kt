package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.StatisticsState
import com.poetscorner.invoiceqr.domain.Invoice

interface ScannerPlatform {
    val versionName: String
    val language: String
    val layoutDirection: LayoutDirection
    val isChangingConfigurations: Boolean
    val portraitLockSupported: Boolean
    val canRequestReview: Boolean
    val filePickerOpen: Boolean

    fun setLanguage(language: String)
    fun pickInvoiceFiles()
    fun exportHistory(filter: HistoryFilter)
    fun exportStatistics(state: StatisticsState)
    fun createBackup()
    fun importBackup()
    fun shareInvoice(invoice: Invoice): Boolean
    fun shareApp(): Boolean
    fun openStore(): Boolean
    fun copyVat(value: String)
    fun openPrivacyPolicy(): Boolean
    suspend fun requestReview(stillAllowed: () -> Boolean)

    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    @Composable
    fun PortraitLock(enabled: Boolean)

    @Composable
    fun SystemBars(darkTheme: Boolean)

    @Composable
    fun Camera(settings: ScannerSettings, continuous: Boolean, zoom: Float, onZoom: (Float) -> Unit,
        onScan: suspend (String) -> Boolean, onTorch: () -> Unit, contentPadding: PaddingValues,
        controls: @Composable () -> Unit)
}
