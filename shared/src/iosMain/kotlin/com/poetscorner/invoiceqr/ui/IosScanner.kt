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

private const val LANGUAGE = "InvoiceQRLanguage"
private const val APPLE_LANGUAGES = "AppleLanguages"

@Composable
fun IosScanner(session: IosInvoiceSession, host: () -> UIViewController?,
    version: String, privacyPolicyUrl: String, storeUrl: String?,
    updateOrientationLock: (Boolean) -> Unit, updateSystemBars: (Boolean) -> Unit) {
    val state = session.controller
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

    SideEffect {
        session.files.historyLabels = { historyExportText(resources) }
        session.files.statisticsLabels = { request, result -> statisticsExportText(resources, resolvedLocale, request, result) }
        session.files.saveDocument = { url ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    val presented = system.exportDocument(url) { saved ->
                        if (continuation.isActive) continuation.resumeWith(Result.success(saved))
                    }
                    if (!presented && continuation.isActive) continuation.resumeWith(Result.failure(
                        IllegalStateException("Unable to open save picker")))
                    continuation.invokeOnCancellation { system.cancelPicker() }
                }
            }
        }
    }
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
                scope.launch {
                    system.pickInvoiceSource(
                        getString(resources, Res.string.import_photos),
                        getString(resources, Res.string.import_files),
                        getString(resources, Res.string.cancel),
                        onSelection = session::importInvoices)
                }
            }

            override fun exportHistory(filter: HistoryFilter) { state.export("zatca-history.csv", true, filter) }
            override fun exportStatistics(state: StatisticsState) {
                if (session.controller.prepareStatisticsExport(state))
                    session.controller.exportStatistics("invoice-statistics.csv")
            }
            override fun createBackup() {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                state.backup("invoiceqr-backup-$today.json")
            }
            override fun importBackup() { system.pickBackup { it.singleOrNull()?.let(session::importBackup) } }

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
