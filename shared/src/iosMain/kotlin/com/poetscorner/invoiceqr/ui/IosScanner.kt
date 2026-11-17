@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.backhandler.BackHandler as SystemBackHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.StatisticsState
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.resources.*
import com.poetscorner.invoiceqr.ui.text.UiText
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import kotlin.time.Clock

/** The app coordinator supplies persistence operations. URLs are sandbox-local or picker copies. */
interface IosInvoiceDocuments {
    fun importInvoices(urls: List<NSURL>)
    fun importBackup(url: NSURL)
    suspend fun writeHistory(name: String, filter: HistoryFilter): NSURL?
    suspend fun writeStatistics(name: String, state: StatisticsState): NSURL?
    suspend fun writeBackup(name: String): NSURL?
    fun removeExport(url: NSURL)
}

private const val LANGUAGE = "InvoiceQRLanguage"
private const val APPLE_LANGUAGES = "AppleLanguages"

@Composable
fun IosScanner(state: ScannerUiState, documents: IosInvoiceDocuments, host: () -> UIViewController?,
    version: String, privacyPolicyUrl: String, storeUrl: String?,
    updateOrientationLock: (Boolean) -> Unit, updateSystemBars: (Boolean) -> Unit) {
    val preferences = remember { NSUserDefaults.standardUserDefaults }
    var selectedLanguage by remember { mutableStateOf(preferences.stringForKey(LANGUAGE).orEmpty()) }
    val hostProvider by rememberUpdatedState(host)
    val system = remember { IosSystemActions { hostProvider() } }
    val resolvedLocale = selectedLanguage.ifEmpty { Locale.current.toLanguageTag() }
    val systemDirection = LocalLayoutDirection.current
    val direction = when (selectedLanguage) {
        "ar" -> LayoutDirection.Rtl
        "en" -> LayoutDirection.Ltr
        else -> systemDirection
    }
    val resources = key(selectedLanguage) { rememberResourceEnvironment() }
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalIosInvoiceLocale provides resolvedLocale) {
        ScannerApp(state, object : ScannerPlatform {
            override val versionName = version
            override val language get() = selectedLanguage
            override val layoutDirection = direction
            override val isChangingConfigurations = false
            override val portraitLockSupported get() = hostProvider()?.traitCollection?.userInterfaceIdiom ==
                platform.UIKit.UIUserInterfaceIdiomPhone
            override val canRequestReview get() = hostProvider()?.view?.window?.windowScene != null
            override val filePickerOpen get() = system.filePickerOpen

            override fun setLanguage(value: String) {
                require(value in setOf("", "ar", "en"))
                if (value.isEmpty()) {
                    preferences.removeObjectForKey(LANGUAGE)
                    preferences.removeObjectForKey(APPLE_LANGUAGES)
                } else {
                    preferences.setObject(value, forKey = LANGUAGE)
                    preferences.setObject(listOf(value), forKey = APPLE_LANGUAGES)
                }
                selectedLanguage = value
            }

            override fun pickInvoiceFiles() {
                system.pickInvoiceFiles { if (it.isNotEmpty()) documents.importInvoices(it) }
            }

            override fun exportHistory(filter: HistoryFilter) {
                export { documents.writeHistory("zatca-history.csv", filter) }
            }

            override fun exportStatistics(state: StatisticsState) {
                export { documents.writeStatistics("invoice-statistics.csv", state) }
            }

            override fun createBackup() {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                export { documents.writeBackup("invoiceqr-backup-$today.json") }
            }

            override fun importBackup() {
                system.pickBackup { it.singleOrNull()?.let(documents::importBackup) }
            }

            private fun export(write: suspend () -> NSURL?) {
                scope.launch {
                    val file = write() ?: return@launch
                    if (!system.exportDocument(file) { documents.removeExport(file) }) {
                        documents.removeExport(file)
                    }
                }
            }

            override fun shareInvoice(invoice: Invoice): Boolean {
                if (hostProvider()?.presentedViewController != null || hostProvider() == null) return false
                scope.launch {
                    val summary = iosInvoiceSummary(invoice, resolvedLocale) { id, args ->
                        getString(resources, id, *args)
                    }
                    if (!system.share(summary)) state.message.value = UiText(Res.string.share_error)
                }
                return true
            }

            override fun shareApp(): Boolean {
                val link = storeUrl ?: return false
                scope.launch {
                    if (!system.share(getString(resources, Res.string.app_name) + "\n" + link)) {
                        state.message.value = UiText(Res.string.share_error)
                    }
                }
                return true
            }

            override fun openStore() = storeUrl?.let(system::openLink) ?: false
            override fun copyVat(value: String) = system.copy(value)
            override fun openPrivacyPolicy() = system.openLink(privacyPolicyUrl).also {
                if (!it) state.message.value = UiText(Res.string.privacy_link_error)
            }
            override suspend fun requestReview(stillAllowed: () -> Boolean) =
                system.requestReview(stillAllowed)

            @Composable
            override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = SystemBackHandler(enabled, onBack)

            @Composable
            override fun PortraitLock(enabled: Boolean) {
                LaunchedEffect(enabled) { updateOrientationLock(enabled) }
            }

            @Composable
            override fun SystemBars(darkTheme: Boolean) {
                SideEffect { updateSystemBars(darkTheme) }
            }

            @Composable
            override fun Camera(settings: ScannerSettings, continuous: Boolean, zoom: Float,
                onZoom: (Float) -> Unit, onScan: suspend (String) -> Boolean, onTorch: () -> Unit,
                contentPadding: PaddingValues, controls: @Composable () -> Unit) =
                IosCameraScanner(settings, continuous, zoom, onZoom, onScan, onTorch, contentPadding, controls)
        })
    }
}
