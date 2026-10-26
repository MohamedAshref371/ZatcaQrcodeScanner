package com.poetscorner.invoiceqr.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.poetscorner.invoiceqr.BuildConfig
import com.poetscorner.invoiceqr.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import com.poetscorner.invoiceqr.billing.SupporterViewModel
import com.poetscorner.invoiceqr.billing.SupporterState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.scanner.MlKitModuleState
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.InvoiceType
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.ui.theme.ZatcaQrcodeScannerTheme
import com.poetscorner.invoiceqr.ui.text.localized
import com.poetscorner.invoiceqr.ui.text.UiText
import java.text.DateFormat
import java.util.Date

@Composable
fun ScannerApp(vm: ScannerViewModel = viewModel(), supporter: SupporterViewModel = viewModel()) {
    // Locale changes update resources in place; the host View can retain its old direction.
    // Drive Compose from the current configuration so language and layout change together.
    val direction = if (LocalConfiguration.current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        ScannerContent(vm, supporter)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerContent(vm: ScannerViewModel, supporter: SupporterViewModel) {
    val historyScreenState = rememberSaveableStateHolder()
    val settingsListState = rememberLazyListState()
    val previousTab by vm.previousTab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mlKitModule by vm.mlKitModule.collectAsStateWithLifecycle()
    val history by vm.historyState.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    var pendingFileAction by rememberSaveable { mutableStateOf<String?>(null) }
    val transferLocked = transfer != null || pendingFileAction != null
    val continuousScan by vm.continuousScan.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val support by supporter.state.collectAsStateWithLifecycle()
    // Preview is presentation state only; it never writes a purchase entitlement.
    var supporterPreview by rememberSaveable { mutableStateOf(false) }
    var previewTheme by rememberSaveable { mutableStateOf("GOLD") }
    val previewActive = supporterPreview && !support.owned
    LaunchedEffect(support.owned) { if (support.owned) supporterPreview = false }
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, haptic) {
        vm.scanFeedback.collect { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        supporter.refresh()
        vm.recordReviewUse()
        vm.refreshMlKit()
        vm.refreshStatisticsPeriod()
    }
    val screen by vm.screen.collectAsStateWithLifecycle()
    // Keep result content across tab changes, but start a new invoice at the top.
    val resultScreenState = key(result?.scanId, result?.checkedAt, result?.invoice?.raw) {
        rememberSaveableStateHolder()
    }
    val titleBarState = rememberTopAppBarState()
    val titleCanScroll by rememberUpdatedState(screen != 0)
    val titleScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(titleBarState,
        canScroll = { titleCanScroll })
    val restoringResult by vm.restoringResult.collectAsStateWithLifecycle()
    LaunchedEffect(screen) { if (screen == 3) vm.refreshMlKit() }
    var reviewOpportunity by remember { mutableIntStateOf(0) }
    var clearDialog by remember { mutableStateOf(false) }
    var invoiceToDelete by remember { mutableStateOf<HistorySummary?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (context.activity()?.isChangingConfigurations != true) {
            vm.resetInvoiceDeleteConfirmation()
            vm.pauseCameraSession()
            snackbar.currentSnackbarData?.dismiss()
            invoiceToDelete = null
        }
    }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var historyTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var historyDateSource by rememberSaveable { mutableStateOf(HistoryDateSource.INVOICE) }
    var historyFilterDialog by rememberSaveable { mutableStateOf(false) }
    val lifecycleState by lifecycle.currentStateAsState()
    val reviewAllowed = reviewOpportunity > 0 && !previewActive && screen == 2 && !busy && !transferLocked && message == null &&
        snackbar.currentSnackbarData == null && !clearDialog && invoiceToDelete == null &&
        !historyFilterDialog && !support.purchasing && lifecycleState == Lifecycle.State.RESUMED
    val reviewStillAllowed by rememberUpdatedState(reviewAllowed)
    LaunchedEffect(reviewOpportunity, reviewAllowed) {
        if (!reviewAllowed) return@LaunchedEffect
        // Let navigation settle; never overlap the camera, results, messages or dialogs.
        delay(600)
        val activity = context.activity() ?: return@LaunchedEffect
        try {
            if (vm.claimReviewAttempt()) requestAppReview(activity) { reviewStillAllowed }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Play review failures are silent and do not cause another automatic attempt.
        }
    }
    var exportFilter by rememberSaveable { mutableStateOf(HistoryFilter()) }
    val activeHistoryFilter = remember(historyQuery, historyFrom, historyTo, historyDateSource) {
        HistoryFilter(historyQuery, historyFrom, historyTo, historyDateSource)
    }
    LaunchedEffect(activeHistoryFilter) { vm.filterHistory(activeHistoryFilter) }
    val visibleHistory = history.forDisplay(activeHistoryFilter)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::gallery) }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        pendingFileAction = null
        it?.let { uri -> vm.export(uri, false, exportFilter) }
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
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingFileAction = null
        it?.let { uri -> vm.importBackup(uri) }
    }
    LaunchedEffect(message, configuration) { message?.let { snackbar.showSnackbar(it.resolve(context)); vm.message.value = null } }
    LaunchedEffect(vm, configuration, resources, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.continuousNotices.collectLatest { notice ->
                val title = when (notice.status) {
                    ContinuousScanStatus.SAVED -> R.string.continuous_scan_saved
                    ContinuousScanStatus.REVIEW -> R.string.continuous_scan_review
                    ContinuousScanStatus.DUPLICATE -> R.string.continuous_scan_duplicate
                    ContinuousScanStatus.SAVE_FAILED -> R.string.history_save_error
                }
                val invoice = notice.result.invoice
                val details = resources.getString(R.string.continuous_scan_details,
                    invoice.value(1).ifBlank { resources.getString(R.string.unknown_seller) },
                    invoice.value(4).ifBlank { resources.getString(R.string.missing) })
                snackbar.currentSnackbarData?.dismiss()
                val action = snackbar.showSnackbar(resources.getString(title) + "\n" + details,
                    actionLabel = resources.getString(R.string.continuous_scan_view_result),
                    duration = SnackbarDuration.Short)
                if (action == SnackbarResult.ActionPerformed) vm.viewContinuousResult(notice)
            }
        }
    }
    BackHandler(screen != 0 || (previousTab != null && previousTab != 0)) { vm.backFromResultOrHome() }
    val darkTheme = when(settings.theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    ZatcaQrcodeScannerTheme(darkTheme = darkTheme,
        goldenTheme = if (previewActive) previewTheme == "GOLD" else support.owned && settings.supporterTheme == "GOLD",
        purpleTheme = if (previewActive) previewTheme == "PURPLE" else support.owned && settings.supporterTheme == "PURPLE") {
        SideEffect {
            // Camera reveals the shared title before layout, and leaves it visible for other tabs.
            if (screen == 0) {
                titleBarState.heightOffset = 0f
                titleBarState.contentOffset = 0f
            }
            context.activity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
        Scaffold(topBar = { TopAppBar(modifier = Modifier.testTag("app_title_bar"),
            scrollBehavior = if (screen == 0) null else titleScrollBehavior, navigationIcon = {
            IconTile(R.drawable.ic_scan, Modifier.padding(start = 16.dp, end = 8.dp))
        }, title = { Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = if (screen == 0) 0.88f else 1f))) }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (screen == 0) 0.88f else 1f), shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp) {
            listOf(0 to R.string.nav_scanner, 1 to R.string.nav_result, 2 to R.string.nav_history,
                4 to R.string.nav_statistics, 3 to R.string.nav_settings).forEach { (index, label) ->
                NavigationBarItem(selected = screen == index, onClick = {
                    if (index == 2 && (screen == 0 || screen == 1)) reviewOpportunity++
                    vm.selectTab(index)
                }, icon = { AppIcon(listOf(R.drawable.ic_scan, R.drawable.ic_receipt,
                    R.drawable.ic_history, R.drawable.ic_settings, R.drawable.ic_statistics)[index]) },
                    label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer))
            }
        } } }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.then(if (screen == 0) Modifier else Modifier.padding(padding)).consumeWindowInsets(padding).imePadding().fillMaxSize()
                .then(if (screen == 0) Modifier else Modifier.nestedScroll(titleScrollBehavior.nestedScrollConnection))) {
                if (busy || restoringResult) LinearProgressIndicator(Modifier.fillMaxWidth())
                transfer?.let { TransferProgress(it) }
                when(screen) {
                    0 -> ScannerHome(settings, continuousScan, busy, { gallery.launch("image/*") },
                        vm::setContinuousScanning, vm::scanned, padding) { vm.update { it.copy(torch = !it.torch) } }
                    1 -> if (result != null) resultScreenState.SaveableStateProvider("result") {
                        ResultScreen(result!!.invoice, result!!.checkedAt, onShare = {
                            if (!shareInvoice(context, result!!.invoice)) vm.message.value = UiText(R.string.share_error)
                        }) {
                            vm.navigate(0)
                        }
                    } else if (!restoringResult) ManualInvoiceEntry(busy, vm::inspectBase64)
                    2 -> {
                        historyScreenState.SaveableStateProvider("history") {
                        HistoryScreen(visibleHistory, historyQuery, { historyQuery = it },
                            onLoadMore = vm::loadMoreHistory, onRetry = vm::retryHistory,
                            onOpen = { vm.open(it.id) }, onDelete = {
                                if (vm.skipInvoiceDeleteConfirmation) vm.delete(it.id)
                                else invoiceToDelete = it
                            }, actions = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                exportFilter = activeHistoryFilter
                                pendingFileAction = "json"
                                json.launch("zatca-history.json")
                            }, enabled = visibleHistory.canExport && !transferLocked, modifier = Modifier.weight(1.15f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) {
                                Text(stringResource(R.string.export_json), style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(onClick = {
                                exportFilter = activeHistoryFilter
                                pendingFileAction = "csv"
                                csv.launch("zatca-history.csv")
                            }, enabled = visibleHistory.canExport && !transferLocked, modifier = Modifier.weight(1.15f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) {
                                Text(stringResource(R.string.export_csv), style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(onClick = { historyFilterDialog = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) {
                                Text(stringResource(R.string.history_filter_search), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { clearDialog = true }, enabled = history.total > 0 && !transferLocked,
                                modifier = Modifier.weight(0.8f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Text(stringResource(R.string.clear), style = MaterialTheme.typography.labelMedium)
                            }
                        } })
                        }
                    }
                    4 -> {
                        val selection by vm.statisticsSelection.collectAsStateWithLifecycle()
                        if (selection != null) {
                            historyScreenState.SaveableStateProvider("statistics_invoices") {
                            val invoices by vm.statisticsInvoices.collectAsStateWithLifecycle()
                            BackHandler { vm.closeStatisticsGroup() }
                            HistoryScreen(invoices, "", {}, vm::loadMoreStatisticsInvoices, vm::retryStatisticsInvoices,
                                onOpen = { vm.openStatisticsInvoice(it.id) }, onDelete = {},
                                groupTitle = statisticsGroupTitle(context, selection!!.request.grouping, selection!!.key),
                                actions = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        OutlinedButton(onClick = vm::closeStatisticsGroup,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                contentColor = MaterialTheme.colorScheme.onSurface)) {
                                            AppIcon(R.drawable.ic_arrow_back, modifier = Modifier.size(ButtonDefaults.IconSize))
                                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.stats_back))
                                        }
                                    }
                                })
                            }
                        } else {
                            historyScreenState.SaveableStateProvider("statistics") {
                            val statistics by vm.statistics.collectAsStateWithLifecycle()
                            StatisticsScreen(statistics, vm::updateStatistics, vm::retryStatistics,
                                sellerSuggestions = vm::sellerSuggestions,
                                onOpenGroup = { vm.openStatisticsGroup(com.poetscorner.invoiceqr.data.StatisticsSelection(statistics.request, it)) },
                                exportEnabled = !transferLocked, onExport = {
                                    if (vm.prepareStatisticsExport(statistics)) {
                                        pendingFileAction = "statistics_csv"
                                        statisticsCsv.launch("invoice-statistics.csv")
                                    }
                                })
                            }
                        }
                    }
                    else -> SettingsScreen(settings, vm::update, support,
                        listState = settingsListState,
                        preview = previewActive, previewTheme = previewTheme,
                        onPreview = { active -> supporterPreview = active; if (active) previewTheme = "GOLD" },
                        onPreviewTheme = { previewTheme = it },
                        transfersEnabled = !transferLocked,
                        onBackup = {
                            pendingFileAction = "backup"
                            backup.launch("invoiceqr-backup-${java.time.LocalDate.now()}.json")
                        },
                        onImportBackup = {
                            pendingFileAction = "import"
                            importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        },
                        mlKitModule = mlKitModule, onDownloadMlKit = vm::downloadMlKit,
                        onEngine = vm::selectScanEngine,
                        onPurchase = { context.activity()?.let(supporter::purchase) }, onRestore = supporter::refresh,
                        onRate = { if (!openAppStore(context)) vm.message.value = UiText(R.string.store_open_error) },
                        onShare = { if (!shareApp(context)) vm.message.value = UiText(R.string.share_error) })
                }
            }
        }
        if (historyFilterDialog) HistoryFilterDialog(historyDateSource, historyFrom, historyTo,
            onDismiss = { historyFilterDialog = false }, onApply = { source, from, to ->
                historyDateSource = source; historyFrom = from; historyTo = to; historyFilterDialog = false
            })
        invoiceToDelete?.let { invoice ->
            key(invoice.id) {
                InvoiceDeleteDialog(invoice.seller.ifBlank { stringResource(R.string.unknown_seller) },
                    onDismiss = { invoiceToDelete = null }, onConfirm = { skip ->
                        vm.delete(invoice.id, skip)
                        invoiceToDelete = null
                    })
            }
        }
        if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text(stringResource(R.string.clear_title)) }, text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { vm.keepLatestDay(); clearDialog = false }) { Text(stringResource(R.string.keep_latest_day)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { vm.clear(); clearDialog = false }) {
                            Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            })
    }
}

@Composable
private fun ManualInvoiceEntry(busy: Boolean, inspect: (String) -> Unit) {
    var raw by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconTile(R.drawable.ic_receipt)
        SectionHeading(stringResource(R.string.no_invoice), stringResource(R.string.manual_base64_hint))
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_base64_label)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            minLines = 4,
            maxLines = 8,
            shape = MaterialTheme.shapes.medium,
            enabled = !busy
        )
        Button(
            onClick = { keyboard?.hide(); inspect(raw.trim()) },
            enabled = raw.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.manual_base64_inspect)) }
    }
}

@Composable
private fun ResultScreen(invoice: Invoice, checkedAt: Long, onShare: () -> Unit, again: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val checkedAtText = remember(checkedAt, locale) {
        DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale).format(Date(checkedAt))
    }
    var advanced by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    var checks by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    var raw by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    val relevantChecks = invoice.checks.filter { invoice.phase2 || it.phase == 1 }
    LazyColumn(Modifier.testTag("result_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(colors = CardDefaults.cardColors(containerColor = if(invoice.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(if (invoice.passed) R.drawable.ic_check else R.drawable.ic_info,
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tint = if (invoice.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(if(invoice.passed) stringResource(R.string.result_passed) else stringResource(R.string.result_failed),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onShare) {
                        AppIcon(R.drawable.ic_share, description = stringResource(R.string.share_invoice))
                    }
                }
                Text(stringResource(R.string.invoice_summary, stringResource(invoice.type.label), stringResource(if (invoice.phase2) R.string.phase_two else R.string.phase_one)))
                invoice.error?.let { Text(it.localized()) }
                if (!invoice.passed) Text(stringResource(R.string.result_review_hint), style = MaterialTheme.typography.bodyMedium)
                relevantChecks.filterNot { it.passed }.forEach { check ->
                    Text(stringResource(R.string.check_status, "✕", check.label.localized(), check.phase),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.result_checked_at, checkedAtText), style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Text(stringResource(R.string.structural_disclaimer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionHeading(stringResource(R.string.invoice_details_heading)) }
        items((1..if (invoice.phase2) 6 else 5).toList()) { tag -> InvoiceField(invoice, tag) }
        if (invoice.phase2) {
            item {
                OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (advanced) R.string.result_hide_advanced else R.string.result_show_advanced))
                }
            }
            if (advanced) items((7..9).toList()) { tag -> InvoiceField(invoice, tag) }
        }
        item {
            TextButton(onClick = { checks = !checks }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (checks) R.string.result_hide_checks else R.string.check_details))
            }
        }
        if (checks) {
            item {
                Text(stringResource(R.string.phase_one_report, stringResource(if (invoice.error == null && invoice.checks.filter { it.phase == 1 }.all { it.passed }) R.string.checks_passed else R.string.checks_failed)))
                if (invoice.phase2) Text(stringResource(R.string.phase_two_report, stringResource(if (invoice.error == null && relevantChecks.all { it.passed }) R.string.structural_passed else R.string.checks_failed)))
            }
            items(relevantChecks) { check -> Text(stringResource(R.string.check_status, if(check.passed) "✓" else "✕", check.label.localized(), check.phase), color = if(check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        }
        item {
            TextButton(onClick = { raw = !raw }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (raw) R.string.result_hide_raw else R.string.raw_qr))
            }
        }
        if (raw) item {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                RawQrText(invoice.raw, Modifier.fillMaxWidth())
            }
        }
        item { Button(onClick = again, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_again)) } }
    }
}

@Composable
private fun InvoiceField(invoice: Invoice, tag: Int) {
    val context = LocalContext.current
    if (tag == 3) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(invoiceTimestampTitle(context, invoice.value(tag)), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    invoiceTimestampFields(context, invoice.value(tag)).forEach { (title, value) ->
                        val line = "$title: $value"
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        return
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(ZatcaParser.names.getValue(tag)), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompositionLocalProvider(LocalLayoutDirection provides
            (if (tag == 2) LayoutDirection.Ltr else LocalLayoutDirection.current)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(invoice.value(tag).ifBlank {
                    if (tag == 9 && invoice.type == InvoiceType.STANDARD) stringResource(R.string.stamp_optional) else stringResource(R.string.missing)
                }, style = (if (tag == 4) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge).let {
                    if (tag >= 2 && invoice.value(tag).isNotBlank()) it.copy(textDirection = TextDirection.Ltr) else it
                }, fontWeight = if (tag == 1 || tag == 4) FontWeight.Bold else FontWeight.Normal)
            }
            if (tag == 2) IconButton(onClick = { copyVat(context, invoice.value(2)) }, enabled = invoice.value(2).isNotBlank()) {
                AppIcon(R.drawable.ic_copy, description = stringResource(R.string.copy_vat))
            }
        }
        }
        if (tag in 6..7) Text(stringResource(R.string.base64_original), style = MaterialTheme.typography.labelSmall)
        if (tag >= 8) Text(stringResource(R.string.base64_binary), style = MaterialTheme.typography.labelSmall)
    } }
}

@Composable
private fun SettingsScreen(s: ScannerSettings, update: ((ScannerSettings) -> ScannerSettings) -> Unit,
    support: SupporterState, mlKitModule: MlKitModuleState,
    listState: LazyListState,
    preview: Boolean, previewTheme: String, onPreview: (Boolean) -> Unit, onPreviewTheme: (String) -> Unit,
    transfersEnabled: Boolean, onBackup: () -> Unit, onImportBackup: () -> Unit,
    onDownloadMlKit: () -> Unit, onEngine: (ScanEngine) -> Unit,
    onPurchase: () -> Unit, onRestore: () -> Unit, onRate: () -> Unit, onShare: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.testTag("settings_list"), state = listState,
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading(stringResource(R.string.settings_heading), stringResource(R.string.settings_subtitle)) }
        item { Text(stringResource(R.string.settings_camera), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary) }
        item {
            Choice(stringResource(R.string.resolution), s.resolution, listOf(
                "SD" to stringResource(R.string.resolution_sd),
                "HD" to stringResource(R.string.resolution_hd),
                "FHD" to stringResource(R.string.resolution_fhd),
                "QHD" to stringResource(R.string.resolution_qhd),
                "UHD" to stringResource(R.string.resolution_uhd)
            ), singleLine = true) { v -> update { it.copy(resolution = v) } }
        }
        item { Choice(stringResource(R.string.scan_frequency), s.speed, listOf("NORMAL" to stringResource(R.string.speed_normal), "FAST" to stringResource(R.string.speed_fast), "FASTEST" to stringResource(R.string.speed_fastest)), singleLine = true) { v -> update { it.copy(speed = v) } } }
        item { Toggle(stringResource(R.string.scan_crop), s.cropEnabled) { v -> update { it.copy(cropEnabled = v) } } }
        item { Text(stringResource(R.string.settings_advanced), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { ScanEnginePicker(s.scanEngine, mlKitModule, onEngine, onDownloadMlKit) }
        if (s.scanEngine == ScanEngine.ZXING) {
            item { Toggle(stringResource(R.string.try_harder), s.harder) { v -> update { it.copy(harder = v) } } }
            item { Toggle(stringResource(R.string.try_denoise), s.denoise) { v -> update { it.copy(denoise = v) } } }
            item { Toggle(stringResource(R.string.try_rotate), s.rotate) { v -> update { it.copy(rotate = v) } } }
            item { Toggle(stringResource(R.string.try_inverted), s.inverted) { v -> update { it.copy(inverted = v) } } }
            item { Choice(stringResource(R.string.binarizer), s.binarizer, listOf("LOCAL_AVERAGE" to stringResource(R.string.bin_local), "GLOBAL_HISTOGRAM" to stringResource(R.string.bin_global), "FIXED_THRESHOLD" to stringResource(R.string.bin_fixed), "BOOL_CAST" to stringResource(R.string.bin_bool))) { v -> update { it.copy(binarizer = v) } } }
        }
        item { Text(stringResource(R.string.camera_settings_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { BackupSection(transfersEnabled, onBackup, onImportBackup) }
        item { Text(stringResource(R.string.settings_app), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { AppLanguagePicker() }
        item { Choice(stringResource(R.string.appearance), s.theme, listOf("SYSTEM" to stringResource(R.string.theme_system), "LIGHT" to stringResource(R.string.theme_light), "DARK" to stringResource(R.string.theme_dark)), singleLine = true) { v -> update { it.copy(theme = v) } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SettingsAction(stringResource(R.string.rate_app), R.drawable.ic_star, onRate)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium) {
                    IconButton(onClick = onShare, modifier = Modifier.size(56.dp)) {
                        AppIcon(R.drawable.ic_share, description = stringResource(R.string.share_app),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { SettingsAction(stringResource(R.string.privacy_policy), R.drawable.ic_info) { showPrivacy = true } }
        item { SettingsAction(stringResource(R.string.about_app), R.drawable.ic_info) { showAbout = true } }
        item { SupporterCard(support, if (preview) previewTheme else s.supporterTheme,
            preview, onPreview, onPurchase, onRestore) { value ->
            if (preview) onPreviewTheme(value) else update { it.copy(supporterTheme = value, goldenTheme = value == "GOLD") }
        } }
    }
    if (showPrivacy) PrivacyPolicyDialog { showPrivacy = false }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false },
        title = { Text(stringResource(R.string.app_name)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            if (preview) Text(stringResource(R.string.supporter_preview_badge), style = MaterialTheme.typography.labelMedium)
            if (support.owned || preview) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.supporter_badge), Modifier.padding(10.dp))
            }
            Text(stringResource(R.string.about_details))
        } }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun SupporterThemePicker(selected: String, onChange: (String) -> Unit) {
    Choice(stringResource(R.string.supporter_theme), selected, listOf(
        "DEFAULT" to stringResource(R.string.default_theme),
        "GOLD" to stringResource(R.string.gold_theme),
        "PURPLE" to stringResource(R.string.purple_theme)
    ), singleLine = true, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), onChange = onChange)
}

@Composable
private fun SupporterCard(state: SupporterState, selectedTheme: String, preview: Boolean,
    onPreview: (Boolean) -> Unit, onPurchase: () -> Unit,
    onRestore: () -> Unit, onTheme: (String) -> Unit) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(R.drawable.ic_heart)
                Text(stringResource(R.string.supporter_pack), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            if (state.owned) {
                Text(stringResource(R.string.supporter_active), color = MaterialTheme.colorScheme.primary)
                SupporterThemePicker(selectedTheme, onTheme)
            } else {
                Text(stringResource(R.string.supporter_compact_description), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.supporter_free_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPurchase,
                        enabled = state.available && !state.loading && !state.purchasing && !state.pending,
                        modifier = Modifier.weight(1.4f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(when {
                            state.pending -> stringResource(R.string.purchase_pending)
                            state.purchasing -> stringResource(R.string.purchase_processing)
                            state.loading -> stringResource(R.string.store_connecting)
                            state.price != null -> stringResource(R.string.support_price, state.price.orEmpty())
                            else -> stringResource(R.string.purchase_unavailable)
                        })
                    }
                    OutlinedButton(onClick = { onPreview(!preview) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("supporter_preview_toggle"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(stringResource(if (preview) R.string.supporter_preview_end else R.string.supporter_preview_start))
                    }
                }
                if (preview) SupporterThemePicker(selectedTheme, onTheme)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it.localized(), style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRestore, enabled = !state.loading && !state.purchasing) {
                    Text(stringResource(R.string.restore_retry))
                }
                if (!state.owned) TextButton(onClick = { showDetails = true }) {
                    Text(stringResource(R.string.supporter_details))
                }
            }
        }
    }
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false },
        title = { Text(stringResource(R.string.supporter_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.supporter_description))
                Text(stringResource(R.string.free_features))
                Text(stringResource(R.string.supporter_preview_description))
                Text(stringResource(R.string.billing_connection_note))
            }
        },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.close)) } })
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked, onCheckedChange = null)
        }
    }
}
@Composable
internal fun Choice(label: String, selected: String, values: List<Pair<String,String>>,
    singleLine: Boolean = false, border: BorderStroke? = null, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium, border = border) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (singleLine) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(values.firstOrNull { it.first == selected }?.second ?: selected,
                        Modifier.weight(1.35f), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(values.firstOrNull { it.first == selected }?.second ?: selected,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                AppIcon(R.drawable.ic_expand, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}

@Composable
private fun SettingsAction(label: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, tint = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            AppIcon(R.drawable.ic_chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
