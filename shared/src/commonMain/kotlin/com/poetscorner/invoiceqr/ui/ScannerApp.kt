package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.about_app
import com.poetscorner.invoiceqr.resources.about_details
import com.poetscorner.invoiceqr.resources.about_version
import com.poetscorner.invoiceqr.resources.app_appearance
import com.poetscorner.invoiceqr.resources.app_name
import com.poetscorner.invoiceqr.resources.app_tagline
import com.poetscorner.invoiceqr.resources.appearance
import com.poetscorner.invoiceqr.resources.base64_binary
import com.poetscorner.invoiceqr.resources.base64_original
import com.poetscorner.invoiceqr.resources.bin_bool
import com.poetscorner.invoiceqr.resources.bin_fixed
import com.poetscorner.invoiceqr.resources.bin_global
import com.poetscorner.invoiceqr.resources.bin_local
import com.poetscorner.invoiceqr.resources.binarizer
import com.poetscorner.invoiceqr.resources.blue_theme
import com.poetscorner.invoiceqr.resources.camera_settings_note
import com.poetscorner.invoiceqr.resources.cancel
import com.poetscorner.invoiceqr.resources.check_details
import com.poetscorner.invoiceqr.resources.check_status
import com.poetscorner.invoiceqr.resources.checks_failed
import com.poetscorner.invoiceqr.resources.checks_passed
import com.poetscorner.invoiceqr.resources.clear_title
import com.poetscorner.invoiceqr.resources.close
import com.poetscorner.invoiceqr.resources.continuous_scan_details
import com.poetscorner.invoiceqr.resources.continuous_scan_duplicate
import com.poetscorner.invoiceqr.resources.continuous_scan_review
import com.poetscorner.invoiceqr.resources.continuous_scan_saved
import com.poetscorner.invoiceqr.resources.continuous_scan_view_result
import com.poetscorner.invoiceqr.resources.copy_vat
import com.poetscorner.invoiceqr.resources.default_theme
import com.poetscorner.invoiceqr.resources.export_csv
import com.poetscorner.invoiceqr.resources.gold_theme
import com.poetscorner.invoiceqr.resources.history_filter_title
import com.poetscorner.invoiceqr.resources.history_save_error
import com.poetscorner.invoiceqr.resources.ic_arrow_back
import com.poetscorner.invoiceqr.resources.ic_check
import com.poetscorner.invoiceqr.resources.ic_chevron
import com.poetscorner.invoiceqr.resources.ic_copy
import com.poetscorner.invoiceqr.resources.ic_delete
import com.poetscorner.invoiceqr.resources.ic_download
import com.poetscorner.invoiceqr.resources.ic_expand
import com.poetscorner.invoiceqr.resources.ic_filter
import com.poetscorner.invoiceqr.resources.ic_history
import com.poetscorner.invoiceqr.resources.ic_info
import com.poetscorner.invoiceqr.resources.ic_receipt
import com.poetscorner.invoiceqr.resources.ic_scan
import com.poetscorner.invoiceqr.resources.ic_settings
import com.poetscorner.invoiceqr.resources.ic_share
import com.poetscorner.invoiceqr.resources.ic_star
import com.poetscorner.invoiceqr.resources.ic_statistics
import com.poetscorner.invoiceqr.resources.ic_warning
import com.poetscorner.invoiceqr.resources.image_import_cancel
import com.poetscorner.invoiceqr.resources.image_import_cancelled
import com.poetscorner.invoiceqr.resources.image_import_cancelled_hint
import com.poetscorner.invoiceqr.resources.image_import_done
import com.poetscorner.invoiceqr.resources.image_import_interrupted
import com.poetscorner.invoiceqr.resources.image_import_page_progress
import com.poetscorner.invoiceqr.resources.image_import_processed_files
import com.poetscorner.invoiceqr.resources.image_import_progress
import com.poetscorner.invoiceqr.resources.image_import_saving
import com.poetscorner.invoiceqr.resources.image_import_stopping
import com.poetscorner.invoiceqr.resources.image_import_summary
import com.poetscorner.invoiceqr.resources.image_import_title
import com.poetscorner.invoiceqr.resources.invoice_category
import com.poetscorner.invoiceqr.resources.invoice_details_heading
import com.poetscorner.invoiceqr.resources.invoice_edit_details
import com.poetscorner.invoiceqr.resources.invoice_edit_details_action
import com.poetscorner.invoiceqr.resources.invoice_note
import com.poetscorner.invoiceqr.resources.invoice_number
import com.poetscorner.invoiceqr.resources.invoice_save_details
import com.poetscorner.invoiceqr.resources.invoice_summary
import com.poetscorner.invoiceqr.resources.invoice_your_data
import com.poetscorner.invoiceqr.resources.lock_portrait
import com.poetscorner.invoiceqr.resources.manual_base64_hint
import com.poetscorner.invoiceqr.resources.manual_base64_inspect
import com.poetscorner.invoiceqr.resources.manual_base64_label
import com.poetscorner.invoiceqr.resources.missing
import com.poetscorner.invoiceqr.resources.multi_frame_recovery
import com.poetscorner.invoiceqr.resources.nav_history
import com.poetscorner.invoiceqr.resources.nav_result
import com.poetscorner.invoiceqr.resources.nav_scanner
import com.poetscorner.invoiceqr.resources.nav_settings
import com.poetscorner.invoiceqr.resources.nav_statistics
import com.poetscorner.invoiceqr.resources.no_invoice
import com.poetscorner.invoiceqr.resources.phase_one
import com.poetscorner.invoiceqr.resources.phase_one_report
import com.poetscorner.invoiceqr.resources.phase_two
import com.poetscorner.invoiceqr.resources.phase_two_report
import com.poetscorner.invoiceqr.resources.privacy_policy
import com.poetscorner.invoiceqr.resources.purple_theme
import com.poetscorner.invoiceqr.resources.rate_app
import com.poetscorner.invoiceqr.resources.raw_qr
import com.poetscorner.invoiceqr.resources.resolution
import com.poetscorner.invoiceqr.resources.resolution_fhd
import com.poetscorner.invoiceqr.resources.resolution_hd
import com.poetscorner.invoiceqr.resources.resolution_qhd
import com.poetscorner.invoiceqr.resources.resolution_sd
import com.poetscorner.invoiceqr.resources.resolution_sd_warning
import com.poetscorner.invoiceqr.resources.resolution_uhd
import com.poetscorner.invoiceqr.resources.resolution_uhd_warning
import com.poetscorner.invoiceqr.resources.result_checked_at
import com.poetscorner.invoiceqr.resources.result_failed
import com.poetscorner.invoiceqr.resources.result_hide_advanced
import com.poetscorner.invoiceqr.resources.result_hide_checks
import com.poetscorner.invoiceqr.resources.result_hide_raw
import com.poetscorner.invoiceqr.resources.result_passed
import com.poetscorner.invoiceqr.resources.result_review_hint
import com.poetscorner.invoiceqr.resources.result_show_advanced
import com.poetscorner.invoiceqr.resources.scan_again
import com.poetscorner.invoiceqr.resources.scan_crop
import com.poetscorner.invoiceqr.resources.scan_frequency
import com.poetscorner.invoiceqr.resources.settings_advanced
import com.poetscorner.invoiceqr.resources.settings_app
import com.poetscorner.invoiceqr.resources.settings_camera
import com.poetscorner.invoiceqr.resources.settings_heading
import com.poetscorner.invoiceqr.resources.settings_subtitle
import com.poetscorner.invoiceqr.resources.share_app
import com.poetscorner.invoiceqr.resources.share_error
import com.poetscorner.invoiceqr.resources.share_invoice
import com.poetscorner.invoiceqr.resources.speed_fast
import com.poetscorner.invoiceqr.resources.speed_fastest
import com.poetscorner.invoiceqr.resources.speed_normal
import com.poetscorner.invoiceqr.resources.stamp_optional
import com.poetscorner.invoiceqr.resources.stats_back
import com.poetscorner.invoiceqr.resources.stats_filters_active
import com.poetscorner.invoiceqr.resources.stats_filters_inactive
import com.poetscorner.invoiceqr.resources.store_open_error
import com.poetscorner.invoiceqr.resources.structural_disclaimer
import com.poetscorner.invoiceqr.resources.structural_passed
import com.poetscorner.invoiceqr.resources.theme_dark
import com.poetscorner.invoiceqr.resources.theme_light
import com.poetscorner.invoiceqr.resources.theme_system
import com.poetscorner.invoiceqr.resources.try_denoise
import com.poetscorner.invoiceqr.resources.try_harder
import com.poetscorner.invoiceqr.resources.try_inverted
import com.poetscorner.invoiceqr.resources.try_rotate
import com.poetscorner.invoiceqr.resources.unknown_seller
import com.poetscorner.invoiceqr.resources.verification_core_fields
import com.poetscorner.invoiceqr.resources.verification_hide_levels
import com.poetscorner.invoiceqr.resources.verification_levels
import com.poetscorner.invoiceqr.resources.verification_local_failed
import com.poetscorner.invoiceqr.resources.verification_local_passed
import com.poetscorner.invoiceqr.resources.verification_not_checked
import com.poetscorner.invoiceqr.resources.verification_not_present
import com.poetscorner.invoiceqr.resources.verification_official
import com.poetscorner.invoiceqr.resources.verification_phase_two_fields
import com.poetscorner.invoiceqr.resources.verification_qr_structure
import com.poetscorner.invoiceqr.resources.verification_signature
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import com.poetscorner.invoiceqr.ui.text.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryDeletionPlan
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import com.poetscorner.invoiceqr.data.InvoiceAnnotation
import com.poetscorner.invoiceqr.data.InvoiceCategories
import com.poetscorner.invoiceqr.data.resource
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.InvoiceType
import com.poetscorner.invoiceqr.domain.asUiText
import com.poetscorner.invoiceqr.domain.resourceId
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.ui.theme.ZatcaQrcodeScannerTheme
import com.poetscorner.invoiceqr.ui.text.localized
import com.poetscorner.invoiceqr.ui.text.resolve
import com.poetscorner.invoiceqr.ui.text.UiText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment

@Composable
fun ScannerApp(vm: ScannerUiState, platform: ScannerPlatform) {
    CompositionLocalProvider(LocalLayoutDirection provides platform.layoutDirection) {
        ScannerContent(vm, platform)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerContent(vm: ScannerUiState, platform: ScannerPlatform) {
    val historyScreenState = rememberSaveableStateHolder()
    var cameraZoom by rememberSaveable { mutableFloatStateOf(0f) }
    val settingsListState = rememberLazyListState()
    val previousTab by vm.previousTab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.historyState.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val transferLocked = transfer != null || platform.filePickerOpen
    val continuousScan by vm.continuousScan.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val locale = uiLocaleTag()
    val resources = rememberResourceEnvironment()
    platform.PortraitLock(settings.lockPortrait && platform.portraitLockSupported)
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, haptic) {
        vm.scanFeedback.collect { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.recordReviewUse()
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
    var reviewOpportunity by remember { mutableIntStateOf(0) }
    var clearDialog by remember { mutableStateOf(false) }
    var pendingDeletePlan by remember { mutableStateOf<HistoryDeletionPlan?>(null) }
    var invoiceToDelete by remember { mutableStateOf<HistorySummary?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!platform.isChangingConfigurations) {
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
    var historyCategory by rememberSaveable { mutableStateOf<Int?>(null) }
    var historyAnnotationQuery by rememberSaveable { mutableStateOf("") }
    var historyFilterDialog by rememberSaveable { mutableStateOf(false) }
    val lifecycleState by lifecycle.currentStateAsState()
    val reviewAllowed = reviewOpportunity > 0 && screen == 2 && !busy && !transferLocked && message == null &&
        snackbar.currentSnackbarData == null && !clearDialog && pendingDeletePlan == null && invoiceToDelete == null &&
        !historyFilterDialog && lifecycleState == Lifecycle.State.RESUMED
    val reviewStillAllowed by rememberUpdatedState(reviewAllowed)
    LaunchedEffect(reviewOpportunity, reviewAllowed) {
        if (!reviewAllowed) return@LaunchedEffect
        // Let navigation settle; never overlap the camera, results, messages or dialogs.
        delay(600)
        try {
            if (platform.canRequestReview && vm.claimReviewAttempt()) platform.requestReview { reviewStillAllowed }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Play review failures are silent and do not cause another automatic attempt.
        }
    }
    val activeHistoryFilter = remember(historyQuery, historyFrom, historyTo, historyDateSource,
        historyCategory, historyAnnotationQuery) {
        HistoryFilter(historyQuery, historyFrom, historyTo, historyDateSource,
            category = historyCategory, annotationQuery = historyAnnotationQuery)
    }
    LaunchedEffect(activeHistoryFilter) { vm.filterHistory(activeHistoryFilter) }
    val visibleHistory = history.forDisplay(activeHistoryFilter)
    val imageImport by vm.imageImport.collectAsStateWithLifecycle()
    val imageImportSummary by vm.imageImportSummary.collectAsStateWithLifecycle()
    LaunchedEffect(message, locale, resources) {
        message?.let {
            snackbar.showSnackbar(it.resolve(resources, locale))
            vm.message.value = null
        }
    }
    LaunchedEffect(vm, resources, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.continuousNotices.collectLatest { notice ->
                val title = when (notice.status) {
                    ContinuousScanStatus.SAVED -> Res.continuous_scan_saved
                    ContinuousScanStatus.REVIEW -> Res.continuous_scan_review
                    ContinuousScanStatus.DUPLICATE -> Res.continuous_scan_duplicate
                    ContinuousScanStatus.SAVE_FAILED -> Res.history_save_error
                }
                val invoice = notice.result.invoice
                val details = getString(resources, Res.continuous_scan_details,
                    invoice.value(1).ifBlank { getString(resources, Res.unknown_seller) },
                    invoice.value(4).ifBlank { getString(resources, Res.missing) })
                snackbar.currentSnackbarData?.dismiss()
                val action = snackbar.showSnackbar(getString(resources, title) + "\n" + details,
                    actionLabel = getString(resources, Res.continuous_scan_view_result),
                    duration = SnackbarDuration.Short)
                if (action == SnackbarResult.ActionPerformed) vm.viewContinuousResult(notice)
            }
        }
    }
    platform.BackHandler(screen != 0 || (previousTab != null && previousTab != 0)) { vm.backFromResultOrHome() }
    val darkTheme = when(settings.theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    ZatcaQrcodeScannerTheme(darkTheme = darkTheme,
        goldenTheme = settings.appTheme == "GOLD",
        purpleTheme = settings.appTheme == "PURPLE",
        blueTheme = settings.appTheme == "BLUE") {
        SideEffect {
            // Camera reveals the shared title before layout, and leaves it visible for other tabs.
            if (screen == 0) {
                titleBarState.heightOffset = 0f
                titleBarState.contentOffset = 0f
            }
        }
        platform.SystemBars(darkTheme)
        val safeWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        Scaffold(contentWindowInsets = safeWindowInsets,
            topBar = { Column { TopAppBar(modifier = Modifier.testTag("app_title_bar"),
            windowInsets = safeWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            scrollBehavior = if (screen == 0) null else titleScrollBehavior, navigationIcon = {
            IconTile(Res.ic_scan, Modifier.padding(start = 16.dp, end = 8.dp))
        }, title = { Column {
            Text(stringResource(Res.app_name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.app_tagline), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = if (screen == 0) 0.88f else 1f)))
            val pageProgress = imageImport?.takeIf {
                it.phase == ImageImportPhase.READING_PAGE && it.pageCount > 0
            }
            if (pageProgress != null)
                LinearProgressIndicator(progress = {
                    (pageProgress.page - 1f) / pageProgress.pageCount
                }, modifier = Modifier.fillMaxWidth())
            else if (busy || restoringResult) LinearProgressIndicator(Modifier.fillMaxWidth())
            imageImport?.let { progress ->
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().testTag("file_import_progress")
                        .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val label = when (progress.phase) {
                            ImageImportPhase.OPENING -> stringResource(Res.image_import_progress,
                                progress.current, progress.total, progress.fileName)
                            ImageImportPhase.READING_PAGE -> stringResource(Res.image_import_page_progress,
                                progress.current, progress.total, progress.fileName, progress.page, progress.pageCount)
                            ImageImportPhase.SAVING -> stringResource(Res.image_import_saving,
                                progress.current, progress.total, progress.fileName)
                        }
                        Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = vm::cancelFileImport, enabled = !progress.cancelling,
                            modifier = Modifier.testTag("file_import_cancel")) {
                            Text(stringResource(if (progress.cancelling) Res.image_import_stopping
                                else Res.image_import_cancel))
                        }
                    }
                }
            }
        } }, bottomBar = {
            val compactNavigation = LocalDensity.current.fontScale >= 1.5f
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (screen == 0) 0.88f else 1f), shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp,
                windowInsets = safeWindowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) {
            listOf(0 to Res.nav_scanner, 1 to Res.nav_result, 2 to Res.nav_history,
                4 to Res.nav_statistics, 3 to Res.nav_settings).forEach { (index, label) ->
                NavigationBarItem(selected = screen == index, onClick = {
                    if (index == 2 && (screen == 0 || screen == 1)) reviewOpportunity++
                    vm.selectTab(index)
                }, icon = { AppIcon(listOf(Res.ic_scan, Res.ic_receipt,
                    Res.ic_history, Res.ic_settings, Res.ic_statistics)[index],
                    description = if (compactNavigation) stringResource(label) else null) },
                    label = if (compactNavigation) null else ({ Text(stringResource(label),
                        style = MaterialTheme.typography.labelMedium) }),
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer))
            }
        } } }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.then(if (screen == 0) Modifier else Modifier.padding(padding)).consumeWindowInsets(padding).imePadding().fillMaxSize()
                .then(if (screen == 0) Modifier else Modifier.nestedScroll(titleScrollBehavior.nestedScrollConnection))) {
                transfer?.let { TransferProgress(it) }
                when(screen) {
                    0 -> ScannerHome(settings, continuousScan, busy, cameraZoom, { cameraZoom = it },
                        platform::pickInvoiceFiles, vm::setContinuousScanning, vm::scanned,
                        platform, padding) { vm.update { it.copy(torch = !it.torch) } }
                    1 -> if (result != null) resultScreenState.SaveableStateProvider("result") {
                        ResultScreen(result!!, vm::saveAnnotation, platform::copyVat, onShare = {
                            if (!platform.shareInvoice(result!!.invoice)) vm.message.value = UiText(Res.share_error)
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
                        val tooltipPosition = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)
                        val filtersActive = historyCategory != null || historyAnnotationQuery.isNotBlank() ||
                            historyFrom != null || historyTo != null
                        val filterState = stringResource(if (filtersActive) Res.stats_filters_active
                            else Res.stats_filters_inactive)
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            TooltipBox(positionProvider = tooltipPosition,
                                tooltip = { PlainTooltip { Text(stringResource(Res.export_csv)) } },
                                state = rememberTooltipState()) {
                                IconButton(onClick = {
                                    platform.exportHistory(activeHistoryFilter)
                                }, enabled = visibleHistory.canExport && !transferLocked,
                                    modifier = Modifier.size(48.dp)) {
                                    AppIcon(Res.ic_download, description = stringResource(Res.export_csv))
                                }
                            }
                            TooltipBox(positionProvider = tooltipPosition,
                                tooltip = { PlainTooltip { Text(stringResource(Res.history_filter_title)) } },
                                state = rememberTooltipState()) {
                                IconButton(onClick = { historyFilterDialog = true },
                                    modifier = Modifier.size(48.dp).semantics { stateDescription = filterState }) {
                                    BadgedBox(badge = { if (filtersActive) Badge() }) {
                                        AppIcon(Res.ic_filter,
                                            description = stringResource(Res.history_filter_title))
                                    }
                                }
                            }
                            TooltipBox(positionProvider = tooltipPosition,
                                tooltip = { PlainTooltip { Text(stringResource(Res.clear_title)) } },
                                state = rememberTooltipState()) {
                                IconButton(onClick = { clearDialog = true },
                                    enabled = history.total > 0 && !transferLocked,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error)) {
                                    AppIcon(Res.ic_delete, description = stringResource(Res.clear_title))
                                }
                            }
                        } }, onClearFilters = {
                            historyQuery = ""
                            historyFrom = null; historyTo = null
                            historyDateSource = HistoryDateSource.INVOICE
                            historyCategory = null; historyAnnotationQuery = ""
                        })
                        }
                    }
                    4 -> {
                        val selection by vm.statisticsSelection.collectAsStateWithLifecycle()
                        if (selection != null) {
                            historyScreenState.SaveableStateProvider("statistics_invoices") {
                            val invoices by vm.statisticsInvoices.collectAsStateWithLifecycle()
                            val breakdown by vm.statisticsGroupBreakdown.collectAsStateWithLifecycle()
                            platform.BackHandler(true) { vm.closeStatisticsGroup() }
                            HistoryScreen(invoices, "", {}, vm::loadMoreStatisticsInvoices, vm::retryStatisticsInvoices,
                                onOpen = { vm.openStatisticsInvoice(it.id) }, onDelete = {},
                                groupTitle = statisticsGroupTitle(selection!!.request, selection!!.key),
                                groupSelection = selection, groupBreakdown = breakdown,
                                onBreakdownRetry = vm::retryStatisticsGroupBreakdown,
                                actions = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        OutlinedButton(onClick = vm::closeStatisticsGroup,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                contentColor = MaterialTheme.colorScheme.onSurface)) {
                                            AppIcon(Res.ic_arrow_back, modifier = Modifier.size(ButtonDefaults.IconSize))
                                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                            Text(stringResource(Res.stats_back))
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
                                    platform.exportStatistics(statistics)
                                })
                            }
                        }
                    }
                    else -> SettingsScreen(settings, vm::update,
                        listState = settingsListState, platform = platform,
                        transfersEnabled = !transferLocked,
                        onRate = { if (!platform.openStore()) vm.message.value = UiText(Res.store_open_error) },
                        onShare = { if (!platform.shareApp()) vm.message.value = UiText(Res.share_error) })
                }
            }
        }
        if (historyFilterDialog) HistoryFilterDialog(historyDateSource, historyFrom, historyTo,
            historyCategory, historyAnnotationQuery,
            onDismiss = { historyFilterDialog = false }, onApply = { source, from, to, category, annotationQuery ->
                historyDateSource = source; historyFrom = from; historyTo = to
                historyCategory = category; historyAnnotationQuery = annotationQuery
                historyFilterDialog = false
            })
        imageImportSummary?.let { summary ->
            AlertDialog(onDismissRequest = vm::dismissImageImportSummary,
                title = { Text(stringResource(when {
                    summary.cancelled -> Res.image_import_cancelled
                    summary.interrupted -> Res.image_import_interrupted
                    else -> Res.image_import_title
                })) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.interrupted) Text(stringResource(Res.image_import_processed_files,
                        summary.processedFiles, summary.totalFiles))
                    Text(stringResource(Res.image_import_summary, summary.added,
                        summary.duplicates, summary.needsReview, summary.invalidQr,
                        summary.unreadable, summary.saveFailed))
                    if (summary.cancelled) Text(stringResource(Res.image_import_cancelled_hint))
                } },
                confirmButton = { TextButton(onClick = vm::dismissImageImportSummary) {
                    Text(stringResource(Res.image_import_done))
                } })
        }
        invoiceToDelete?.let { invoice ->
            key(invoice.id) {
                InvoiceDeleteDialog(invoice.seller.ifBlank { stringResource(Res.unknown_seller) },
                    onDismiss = { invoiceToDelete = null }, onConfirm = { skip ->
                        vm.delete(invoice.id, skip)
                        invoiceToDelete = null
                    })
            }
        }
        if (clearDialog) HistoryDeleteSelectionDialog(onDismiss = { clearDialog = false },
            onDelete = { source, period ->
                pendingDeletePlan = HistoryDeletionPlan.create(source, period)
                clearDialog = false
            })
        pendingDeletePlan?.let { plan ->
            HistoryDeleteConfirmationDialog(plan, vm::historyDeletionCount,
                onDismiss = { pendingDeletePlan = null },
                onConfirm = { vm.deleteHistory(plan); pendingDeletePlan = null })
        }
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
        IconTile(Res.ic_receipt)
        SectionHeading(stringResource(Res.no_invoice), stringResource(Res.manual_base64_hint))
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.manual_base64_label)) },
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
        ) { Text(stringResource(Res.manual_base64_inspect)) }
    }
}

@Composable
private fun ResultScreen(result: ScanResult, onSaveAnnotation: (InvoiceAnnotation) -> Unit,
    copyVat: (String) -> Unit, onShare: () -> Unit, again: () -> Unit) {
    val invoice = result.invoice
    val checkedAt = result.checkedAt
    val locale = uiLocaleTag()
    val checkedAtText = remember(checkedAt, locale) { formatCheckedAt(checkedAt, locale) }
    var advanced by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    var checks by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    var raw by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    val relevantChecks = invoice.checks.filter { invoice.phase2 || it.phase == 1 }
    LazyColumn(Modifier.testTag("result_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(colors = CardDefaults.cardColors(containerColor = if(invoice.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(if (invoice.passed) Res.ic_check else Res.ic_info,
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tint = if (invoice.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(if(invoice.passed) stringResource(Res.result_passed) else stringResource(Res.result_failed),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onShare) {
                        AppIcon(Res.ic_share, description = stringResource(Res.share_invoice))
                    }
                }
                Text(stringResource(Res.invoice_summary, stringResource(invoice.type.label.resourceId), stringResource(if (invoice.phase2) Res.phase_two else Res.phase_one)))
                invoice.error?.let { Text(it.asUiText().localized()) }
                if (!invoice.passed) Text(stringResource(Res.result_review_hint), style = MaterialTheme.typography.bodyMedium)
                relevantChecks.filterNot { it.passed }.forEach { check ->
                    Text(stringResource(Res.check_status, "✕", check.label.asUiText().localized(), check.phase),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(Res.result_checked_at, checkedAtText), style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Text(stringResource(Res.structural_disclaimer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (result.scanId != null) item {
            InvoiceAnnotationCard(result.annotation, onSaveAnnotation)
        }
        item {
            Text(stringResource(Res.invoice_details_heading),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items((1..if (invoice.phase2) 6 else 5).toList()) { tag -> InvoiceField(invoice, tag, copyVat) }
        if (invoice.phase2) {
            item {
                OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (advanced) Res.result_hide_advanced else Res.result_show_advanced))
                }
            }
            if (advanced) items((7..9).toList()) { tag -> InvoiceField(invoice, tag, copyVat) }
        }
        item { VerificationLevels(invoice) }
        item {
            TextButton(onClick = { checks = !checks }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (checks) Res.result_hide_checks else Res.check_details))
            }
        }
        if (checks) {
            item {
                Text(stringResource(Res.phase_one_report, stringResource(if (invoice.error == null && invoice.checks.filter { it.phase == 1 }.all { it.passed }) Res.checks_passed else Res.checks_failed)))
                if (invoice.phase2) Text(stringResource(Res.phase_two_report, stringResource(if (invoice.error == null && relevantChecks.all { it.passed }) Res.structural_passed else Res.checks_failed)))
            }
            items(relevantChecks) { check -> Text(stringResource(Res.check_status, if(check.passed) "✓" else "✕", check.label.asUiText().localized(), check.phase), color = if(check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        }
        item {
            TextButton(onClick = { raw = !raw }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (raw) Res.result_hide_raw else Res.raw_qr))
            }
        }
        if (raw) item {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                RawQrText(invoice.raw, Modifier.fillMaxWidth())
            }
        }
        item { Button(onClick = again, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.scan_again)) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceAnnotationCard(annotation: InvoiceAnnotation, onSave: (InvoiceAnnotation) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var number by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.invoice_your_data), style = MaterialTheme.typography.titleSmall)
                if (annotation.invoiceNumber.isNotBlank() || annotation.category != 0 || annotation.note.isNotBlank()) {
                    val numberText = annotation.invoiceNumber.takeIf { it.isNotBlank() }
                        ?.let { stringResource(Res.invoice_number) + ": " + it }
                    val categoryText = annotation.category.takeIf { it != 0 }
                        ?.let { stringResource(InvoiceCategories.resource(it)) }
                    if (numberText != null && categoryText != null) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val combined = "$numberText · $categoryText"
                            val fits = rememberTextMeasurer().measure(combined,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                softWrap = false).size.width <= with(LocalDensity.current) { maxWidth.roundToPx() }
                            if (fits) Text(combined, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(numberText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                Text(categoryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        numberText?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall) }
                        categoryText?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall) }
                    }
                    if (annotation.note.isNotBlank()) Text(annotation.note, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val editDescription = stringResource(Res.invoice_edit_details_action)
            TextButton(onClick = {
                number = annotation.invoiceNumber; category = annotation.category; note = annotation.note
                editing = true
            }, modifier = Modifier.semantics { contentDescription = editDescription }) {
                Text(stringResource(Res.invoice_edit_details))
            }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false },
        title = { Text(stringResource(Res.invoice_your_data)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(number, { number = it.take(120) },
                    label = { Text(stringResource(Res.invoice_number)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(value = stringResource(InvoiceCategories.resource(category)),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(Res.invoice_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().testTag("invoice_annotation_category")
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }) {
                        InvoiceCategories.ids.forEach { id ->
                            DropdownMenuItem(text = { Text(stringResource(InvoiceCategories.resource(id))) },
                                onClick = { category = id; categoryExpanded = false },
                                modifier = Modifier.semantics { selected = category == id })
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(2_000) },
                    label = { Text(stringResource(Res.invoice_note)) },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = {
            onSave(InvoiceAnnotation(number.trim(), category, note.trim()))
            editing = false
        }) { Text(stringResource(Res.invoice_save_details)) } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(Res.cancel)) } })
}

@Composable
fun VerificationLevels(invoice: Invoice) {
    var expanded by rememberSaveable(invoice.raw) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (expanded) Res.verification_hide_levels else Res.verification_levels))
    }
    if (!expanded) return
    val corePassed = invoice.error == null && invoice.checks.filter { it.phase == 1 }.all { it.passed }
    val phaseTwoPassed = invoice.error == null && invoice.checks.filter { it.phase == 2 }.all { it.passed }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VerificationLevelRow(Res.verification_qr_structure,
                if (invoice.error == null) Res.verification_local_passed else Res.verification_local_failed)
            VerificationLevelRow(Res.verification_core_fields,
                if (corePassed) Res.verification_local_passed else Res.verification_local_failed)
            VerificationLevelRow(Res.verification_phase_two_fields, when {
                !invoice.phase2 -> Res.verification_not_present
                phaseTwoPassed -> Res.verification_local_passed
                else -> Res.verification_local_failed
            })
            VerificationLevelRow(Res.verification_signature,
                if (invoice.phase2) Res.verification_not_checked else Res.verification_not_present)
            VerificationLevelRow(Res.verification_official, Res.verification_not_checked)
        }
    }
}

@Composable
private fun VerificationLevelRow(label: StringResource, status: StringResource) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(status), style = MaterialTheme.typography.bodySmall,
            color = if (status == Res.verification_local_failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InvoiceField(invoice: Invoice, tag: Int, copyVat: (String) -> Unit) {
    if (tag == 3) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(invoiceTimestampTitle(invoice.value(tag)), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    invoiceTimestampFields(invoice.value(tag)).forEach { (title, value) ->
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
        Text(stringResource(ZatcaParser.names.getValue(tag).resourceId), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompositionLocalProvider(LocalLayoutDirection provides
            (if (tag == 2) LayoutDirection.Ltr else LocalLayoutDirection.current)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(invoice.value(tag).ifBlank {
                    if (tag == 9 && invoice.type == InvoiceType.STANDARD) stringResource(Res.stamp_optional) else stringResource(Res.missing)
                }, style = (if (tag == 4) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge).let {
                    if (tag >= 2 && invoice.value(tag).isNotBlank()) it.copy(textDirection = TextDirection.Ltr) else it
                }, fontWeight = if (tag == 1 || tag == 4) FontWeight.Bold else FontWeight.Normal)
            }
            if (tag == 2) IconButton(onClick = { copyVat(invoice.value(2)) }, enabled = invoice.value(2).isNotBlank()) {
                AppIcon(Res.ic_copy, description = stringResource(Res.copy_vat))
            }
        }
        }
        if (tag in 6..7) Text(stringResource(Res.base64_original), style = MaterialTheme.typography.labelSmall)
        if (tag >= 8) Text(stringResource(Res.base64_binary), style = MaterialTheme.typography.labelSmall)
    } }
}

@Composable
private fun SettingsScreen(s: ScannerSettings, update: ((ScannerSettings) -> ScannerSettings) -> Unit,
    listState: LazyListState, platform: ScannerPlatform,
    transfersEnabled: Boolean,
    onRate: () -> Unit, onShare: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.testTag("settings_list"), state = listState,
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading(stringResource(Res.settings_heading), stringResource(Res.settings_subtitle)) }
        item { Text(stringResource(Res.settings_camera), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary) }
        item {
            Choice(stringResource(Res.resolution), s.resolution, listOf(
                "SD" to stringResource(Res.resolution_sd),
                "HD" to stringResource(Res.resolution_hd),
                "FHD" to stringResource(Res.resolution_fhd),
                "QHD" to stringResource(Res.resolution_qhd),
                "UHD" to stringResource(Res.resolution_uhd)
            ), singleLine = true, warningDescription = when (s.resolution) {
                "SD" -> stringResource(Res.resolution_sd_warning)
                "UHD" -> stringResource(Res.resolution_uhd_warning)
                else -> null
            }) { v -> update { it.copy(resolution = v) } }
        }
        item { Choice(stringResource(Res.scan_frequency), s.speed, listOf("NORMAL" to stringResource(Res.speed_normal), "FAST" to stringResource(Res.speed_fast), "FASTEST" to stringResource(Res.speed_fastest)), singleLine = true) { v -> update { it.copy(speed = v) } } }
        item { Toggle(stringResource(Res.scan_crop), s.cropEnabled) { v -> update { it.copy(cropEnabled = v) } } }
        item { Text(stringResource(Res.settings_advanced), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { Toggle(stringResource(Res.try_harder), s.harder) { v -> update { it.copy(harder = v) } } }
        item { Toggle(stringResource(Res.try_denoise), s.denoise) { v -> update { it.copy(denoise = v) } } }
        item { Toggle(stringResource(Res.try_rotate), s.rotate) { v -> update { it.copy(rotate = v) } } }
        item { Toggle(stringResource(Res.try_inverted), s.inverted) { v -> update { it.copy(inverted = v) } } }
        item { Choice(stringResource(Res.binarizer), s.binarizer, listOf("LOCAL_AVERAGE" to stringResource(Res.bin_local), "GLOBAL_HISTOGRAM" to stringResource(Res.bin_global), "FIXED_THRESHOLD" to stringResource(Res.bin_fixed), "BOOL_CAST" to stringResource(Res.bin_bool))) { v -> update { it.copy(binarizer = v) } } }
        item { Toggle(stringResource(Res.multi_frame_recovery), s.multiFrameRecovery) { v ->
            update { it.copy(multiFrameRecovery = v) }
        } }
        item { Text(stringResource(Res.camera_settings_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { BackupSection(transfersEnabled, platform::createBackup, platform::importBackup) }
        item { Text(stringResource(Res.settings_app), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { AppLanguagePicker(platform.language, platform::setLanguage) }
        item { AppThemePicker(s.appTheme) { value ->
            update { it.copy(appTheme = value, goldenTheme = value == "GOLD") }
        } }
        item { Choice(stringResource(Res.appearance), s.theme, listOf("SYSTEM" to stringResource(Res.theme_system), "LIGHT" to stringResource(Res.theme_light), "DARK" to stringResource(Res.theme_dark)), singleLine = true) { v -> update { it.copy(theme = v) } } }
        if (platform.portraitLockSupported) {
            item { Toggle(stringResource(Res.lock_portrait), s.lockPortrait) { v -> update { it.copy(lockPortrait = v) } } }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SettingsAction(stringResource(Res.rate_app), Res.ic_star, onRate)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium) {
                    IconButton(onClick = onShare, modifier = Modifier.size(56.dp)) {
                        AppIcon(Res.ic_share, description = stringResource(Res.share_app),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { SettingsAction(stringResource(Res.privacy_policy), Res.ic_info) { showPrivacy = true } }
        item { SettingsAction(stringResource(Res.about_app), Res.ic_info) { showAbout = true } }
    }
    if (showPrivacy) PrivacyPolicyDialog(platform::openPrivacyPolicy) { showPrivacy = false }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false },
        title = { Text(stringResource(Res.app_name)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.about_version, platform.versionName))
            Text(stringResource(Res.about_details))
        } }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(Res.close)) } })
}

@Composable
private fun AppThemePicker(selected: String, onChange: (String) -> Unit) {
    Choice(stringResource(Res.app_appearance), selected, listOf(
        "DEFAULT" to stringResource(Res.default_theme),
        "GOLD" to stringResource(Res.gold_theme),
        "PURPLE" to stringResource(Res.purple_theme),
        "BLUE" to stringResource(Res.blue_theme)
    ), singleLine = true, onChange = onChange)
}

@Composable
private fun SettingsAction(label: String, icon: DrawableResource, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, tint = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            AppIcon(Res.ic_chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
