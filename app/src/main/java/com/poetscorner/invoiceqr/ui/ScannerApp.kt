package com.poetscorner.invoiceqr.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler as AndroidBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poetscorner.invoiceqr.BuildConfig
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.StatisticsState
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.selectedInvoiceUris
import java.time.LocalDate

@Composable
fun ScannerApp(vm: ScannerViewModel = viewModel()) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context.activity()
    val supportsPortraitLock = portraitLockApplies(true,
        configuration.smallestScreenWidthDp, activity?.isInMultiWindowMode == true)
    // The resolved locale can change without updating Configuration.layoutDirection.
    val direction = if (TextUtils.getLayoutDirectionFromLocale(configuration.locales[0]) == View.LAYOUT_DIRECTION_RTL)
        LayoutDirection.Rtl else LayoutDirection.Ltr
    var selectedLanguage by remember(configuration) {
        mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        selectedLanguage = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
    }

    var pendingFileAction by rememberSaveable { mutableStateOf<String?>(null) }
    var exportFilter by rememberSaveable { mutableStateOf(HistoryFilter()) }
    val invoiceFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) it.data?.selectedInvoiceUris()?.let(vm::importInvoiceFiles)
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        pendingFileAction = null
        it?.let { uri -> vm.export(uri, true, exportFilter) }
    }
    val statisticsCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        pendingFileAction = null
        if (it != null) vm.exportStatistics(it) else vm.cancelStatisticsExport()
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        pendingFileAction = null
        it?.let { uri -> vm.backup(uri) }
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingFileAction = null
        it?.let { uri -> vm.importBackup(uri) }
    }

    ScannerApp(vm, object : ScannerPlatform {
        override val versionName = BuildConfig.VERSION_NAME
        override val language = selectedLanguage
        override val layoutDirection = direction
        override val isChangingConfigurations get() = activity?.isChangingConfigurations == true
        override val portraitLockSupported = supportsPortraitLock
        override val canRequestReview = activity != null
        override val filePickerOpen = pendingFileAction != null

        override fun setLanguage(language: String) {
            selectedLanguage = language
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }

        override fun pickInvoiceFiles() {
            invoiceFiles.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            })
        }

        override fun exportHistory(filter: HistoryFilter) {
            exportFilter = filter
            pendingFileAction = "csv"
            csv.launch("zatca-history.csv")
        }

        override fun exportStatistics(state: StatisticsState) {
            if (vm.prepareStatisticsExport(state)) {
                pendingFileAction = "statistics_csv"
                statisticsCsv.launch("invoice-statistics.csv")
            }
        }

        override fun createBackup() {
            pendingFileAction = "backup"
            backup.launch("invoiceqr-backup-${LocalDate.now()}.json")
        }

        override fun importBackup() {
            pendingFileAction = "import"
            backupImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }

        override fun shareInvoice(invoice: Invoice) = shareInvoice(context, invoice)
        override fun shareApp() = shareApp(context)
        override fun openStore() = openAppStore(context)
        override fun copyVat(value: String) = copyVat(context, value)

        override fun openPrivacyPolicy(): Boolean = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.PRIVACY_POLICY_URL.toUri()))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.privacy_link_error, Toast.LENGTH_SHORT).show()
            false
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.privacy_link_error, Toast.LENGTH_SHORT).show()
            false
        }

        override suspend fun requestReview(stillAllowed: () -> Boolean) {
            activity?.let { requestAppReview(it, stillAllowed) }
        }

        @Composable
        override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = AndroidBackHandler(enabled, onBack)

        @Composable
        override fun PortraitLock(enabled: Boolean) {
            LaunchedEffect(activity, enabled) {
                activity?.let {
                    val orientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (it.requestedOrientation != orientation) it.requestedOrientation = orientation
                }
            }
        }

        @Composable
        override fun SystemBars(darkTheme: Boolean) {
            SideEffect {
                activity?.window?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }
        }

        @Composable
        override fun Camera(settings: ScannerSettings, continuous: Boolean, zoom: Float, onZoom: (Float) -> Unit,
            onScan: suspend (String) -> Boolean, onTorch: () -> Unit, contentPadding: PaddingValues,
            controls: @Composable () -> Unit) =
            CameraScanner(settings, continuous, zoom, onZoom, onScan, onTorch, contentPadding, controls)
    })
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
