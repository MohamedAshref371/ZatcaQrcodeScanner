package com.poetscorner.invoiceqr.ui

import android.app.Application
import androidx.core.content.ContextCompat
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.ui.text.UiText
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.*
import com.poetscorner.invoiceqr.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.Writer

data class ScanResult(val invoice: Invoice, val checkedAt: Long, val scanId: Long? = null)

class ScannerViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {
    private val db = ScanDatabase.open(app)
    private val repository = SettingsRepository(app)
    private val mlKitController = MlKitModuleController(GoogleMlKitModuleInstaller(app), viewModelScope)
    val mlKitModule = mlKitController.state
    fun refreshMlKit() = mlKitController.refresh()
    fun downloadMlKit() = mlKitController.download()
    fun selectScanEngine(engine: ScanEngine) {
        if (engine == ScanEngine.ML_KIT && !mlKitModule.value.ready) return
        update { it.copy(scanEngine = engine) }
    }
    init { refreshMlKit() }
    private val reviewRepository = ReviewRepository(app)
    fun recordReviewUse() { viewModelScope.launch {
        try { reviewRepository.recordUse() } catch (_: java.io.IOException) { /* Optional tracking. */ }
    } }
    suspend fun claimReviewAttempt(): Boolean = try {
        reviewRepository.claimAttempt()
    } catch (_: java.io.IOException) { false }
    val settings = combine(repository.settings, mlKitModule) { saved, module ->
        saved.copy(scanEngine = module.effectiveEngine(saved.scanEngine))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScannerSettings())
    private val historyPager = HistoryPager(db.scans(), viewModelScope)
    val historyState = historyPager.state
    fun filterHistory(filter: HistoryFilter) = historyPager.setFilter(filter)
    fun loadMoreHistory() = historyPager.loadMore()
    fun retryHistory() = historyPager.refresh()
    private val statisticsFilters = StatisticsFilterRepository(app)
    init {
        val restored = savedState.get<StatisticsRequest>("statistics_request")
        val persisted = statisticsFilters.load()
        savedState["statistics_request"] = if (statisticsFilters.hasSavedFilters) {
            persisted.copy(grouping = restored?.grouping ?: persisted.grouping,
                sort = restored?.sort ?: persisted.sort, descending = restored?.descending ?: persisted.descending)
        } else (restored ?: persisted).resolvePeriod()
    }
    private val statisticsRequest = savedState.getStateFlow("statistics_request", StatisticsRequest())
    private val statisticsRevision = MutableStateFlow(0L)
    private var cachedStatistics: StatisticsState? = null
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val statistics = combine(statisticsRequest, statisticsRevision) { request, _ -> request }
        .flatMapLatest { request ->
            db.scans().observeCounts(HistoryQueries(HistoryFilter()).counts()).transformLatest {
                emit(cachedStatistics?.copy(request = request, loading = true, failed = false) ?: StatisticsState(request))
                val updated = withContext(Dispatchers.IO) {
                    val result = db.statistics(request)
                    StatisticsState(request, result, loading = false)
                }
                cachedStatistics = updated
                emit(updated)
            }.onStart {
                emit(cachedStatistics?.copy(request = request, loading = true, failed = false) ?: StatisticsState(request))
            }.catch { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                emit(cachedStatistics?.copy(request = request, loading = false, failed = true)
                    ?: StatisticsState(request, loading = false, failed = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsState(statisticsRequest.value))
    fun updateStatistics(request: StatisticsRequest) {
        val mode = if (request.excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else request.vatMode
        val resolved = request.withVatFilter(mode, if (mode == StatisticsVatMode.EXCLUDE) request.excludedVat else request.vat).resolvePeriod()
        statisticsFilters.save(resolved)
        savedState["statistics_request"] = resolved
    }
    fun refreshStatisticsPeriod() {
        val request = statisticsRequest.value
        val resolved = request.resolvePeriod()
        if (request != resolved) savedState["statistics_request"] = resolved
    }
    fun retryStatistics() { statisticsRevision.value++ }
    suspend fun sellerSuggestions(query: String): List<String> =
        db.scans().sellerSuggestions(query.trim().lowercase(java.util.Locale.ROOT))
    val statisticsSelection = savedState.getStateFlow<StatisticsSelection?>("statistics_group", null)
    private val statisticsGroupPager by lazy {
        HistoryPager(db.scans(), viewModelScope).also { pager ->
            statisticsSelection.value?.let { pager.setFilter(HistoryFilter(statisticsSelection = it)) }
        }
    }
    val statisticsInvoices get() = statisticsGroupPager.state
    fun openStatisticsGroup(selection: StatisticsSelection) {
        savedState["statistics_group"] = selection
        statisticsGroupPager.setFilter(HistoryFilter(statisticsSelection = selection))
    }
    fun closeStatisticsGroup() { savedState["statistics_group"] = null }
    fun loadMoreStatisticsInvoices() = statisticsGroupPager.loadMore()
    fun retryStatisticsInvoices() = statisticsGroupPager.refresh()
    private var pendingStatisticsExport: StatisticsState? = null
    fun prepareStatisticsExport(state: StatisticsState): Boolean {
        if (state.loading || state.failed || state.result.groups.isEmpty()) return false
        pendingStatisticsExport = state
        savedState["statistics_export_request"] = state.request
        return true
    }
    fun cancelStatisticsExport() {
        pendingStatisticsExport = null
        savedState.remove<StatisticsRequest>("statistics_export_request")
    }
    fun exportStatistics(uri: Uri): Job? {
        val snapshot = pendingStatisticsExport
        val request = savedState.get<StatisticsRequest>("statistics_export_request") ?: return null
        val job = startTransfer(TransferKind.EXPORT) {
            val result = snapshot?.result ?: db.statistics(request)
            val context = ContextCompat.getContextForLanguage(getApplication())
            val headers = listOf(request.grouping.label(), R.string.stats_count_label,
                if (result.summary.missingTotal > 0) R.string.stats_available_total else R.string.tag_total,
                if (result.summary.missingTax > 0) R.string.stats_available_tax else R.string.tag_tax,
                R.string.stats_missing_total_count, R.string.stats_missing_tax_count).map(context::getString)
            outputDocument(uri) { writer ->
                StatisticsExporter.write(writer, result, headers, context.getString(R.string.stats_summary)) {
                    statisticsGroupTitle(context, request.grouping, it)
                }
            }
            UiText(R.string.stats_export_success)
        }
        if (job != null) cancelStatisticsExport()
        return job
    }
    private val mutableResult = MutableStateFlow<ScanResult?>(null)
    val result = mutableResult.asStateFlow()
    val screen = savedState.getStateFlow("screen", 0)
    val previousTab = savedState.getStateFlow<Int?>("previous_tab", null)
    private val mutableRestoringResult = MutableStateFlow(false)
    val restoringResult = mutableRestoringResult.asStateFlow()
    private var resultLoad: Job? = null
    private var resultVersion = 0L
    val message = MutableStateFlow<UiText?>(null)
    val busy = MutableStateFlow(false)
    private val mutableTransfer = MutableStateFlow<DataTransferState?>(null)
    val transfer = mutableTransfer.asStateFlow()
    init {
        savedState.get<Long>("result_scan_id")?.let { loadResult(it, navigate = false) }
    }

    fun navigate(screen: Int) {
        savedState["previous_tab"] = null
        moveTo(screen)
    }

    fun selectTab(screen: Int) {
        if (this.screen.value == screen) return
        savedState["previous_tab"] = this.screen.value
        moveTo(screen, clearCameraResult = false)
    }

    private fun moveTo(screen: Int, clearCameraResult: Boolean = true) {
        if (screen == 4) refreshStatisticsPeriod()
        if (screen != 1) savedState["result_return_screen"] = 0
        if (screen == 0 && clearCameraResult) clearResult()
        savedState["screen"] = screen
    }

    fun clearResult() {
        resultVersion++
        resultLoad?.cancel()
        resultLoad = null
        mutableRestoringResult.value = false
        savedState.remove<Long>("result_scan_id")
        mutableResult.value = null
    }

    private fun showResult(scan: ScanResult) {
        savedState["previous_tab"] = null
        savedState["result_return_screen"] = 0
        clearResult()
        scan.scanId?.let { savedState["result_scan_id"] = it }
        mutableResult.value = scan
        savedState["screen"] = 1
    }

    private fun loadResult(id: Long, navigate: Boolean) {
        clearResult()
        // Persist only the row ID, never a potentially large QR payload in the saved-state Bundle.
        savedState["result_scan_id"] = id
        mutableRestoringResult.value = true
        val version = resultVersion
        resultLoad = viewModelScope.launch {
            try {
                val scan = db.scans().byId(id)
                if (scan == null) {
                    savedState.remove<Long>("result_scan_id")
                    message.value = UiText(R.string.history_entry_missing)
                } else {
                    mutableResult.value = withContext(Dispatchers.Default) {
                        ScanResult(ZatcaParser.parse(scan.raw), scan.scannedAt, scan.id)
                    }
                    if (navigate) savedState["screen"] = 1
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                message.value = UiText(R.string.history_load_error)
            } finally {
                if (version == resultVersion) mutableRestoringResult.value = false
            }
        }
    }
    private val mutableContinuousScan = MutableStateFlow(ContinuousScanState())
    val continuousScan = mutableContinuousScan.asStateFlow()
    private val mutableScanFeedback = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scanFeedback = mutableScanFeedback.asSharedFlow()
    private val mutableContinuousNotices = MutableSharedFlow<ContinuousScanNotice>(extraBufferCapacity = 1)
    val continuousNotices = mutableContinuousNotices.asSharedFlow()
    private var cameraSession = 0L
    fun setContinuousScanning(enabled: Boolean) {
        if (mutableContinuousScan.value.enabled == enabled) return
        cameraSession++
        mutableContinuousScan.value = mutableContinuousScan.value.copy(enabled = enabled, notice = null)
    }
    fun resetCameraSession() {
        cameraSession++
        mutableContinuousScan.value = ContinuousScanState()
    }
    fun pauseCameraSession() {
        // Ignore in-flight callbacks, keeping the session choice and counter through screen lock.
        cameraSession++
        mutableContinuousScan.value = mutableContinuousScan.value.copy(notice = null)
    }
    fun viewContinuousResult(notice: ContinuousScanNotice? = mutableContinuousScan.value.notice) {
        notice?.let { showResult(it.result) }
    }
    private val settingsMutex = Mutex()
    fun update(change: (ScannerSettings) -> ScannerSettings) { viewModelScope.launch {
        settingsMutex.withLock { repository.save(change(repository.settings.first())) }
    } }
    // Keep saving an accepted scan even when showing its result disposes the camera.
    suspend fun scanned(raw: String): Boolean {
        val session = cameraSession
        val continuous = mutableContinuousScan.value.enabled
        return viewModelScope.async { process(raw, session, continuous) }.await()
    }
    fun inspectBase64(raw: String) {
        if (busy.value || raw.isBlank()) return
        busy.value = true
        viewModelScope.launch {
            try { process(raw) }
            finally { busy.value = false }
        }
    }
    private suspend fun process(raw: String, session: Long? = null, continuous: Boolean = false): Boolean {
        fun currentSession() = session == null || session == cameraSession
        val invoice = withContext(Dispatchers.Default) { ZatcaParser.parse(raw) }
        if (invoice.error != null) {
            if (currentSession()) message.value = UiText(R.string.scan_invalid)
            return false
        }
        val checkedAt = System.currentTimeMillis()
        try {
            val saved = withContext(Dispatchers.Default) { db.scans().saveIfNew(ScanEntity.from(invoice, checkedAt)) }
            if (!saved.alreadyExists) {
                try { reviewRepository.recordSavedInvoice(saved.scan.payloadKey) }
                catch (_: java.io.IOException) { /* Review tracking must not affect scan results. */ }
            }
            val savedInvoice = if (saved.alreadyExists) withContext(Dispatchers.Default) {
                ZatcaParser.parse(saved.scan.raw)
            } else invoice
            if (currentSession()) {
                val scan = ScanResult(savedInvoice, saved.scan.scannedAt, saved.scan.id)
                if (continuous) {
                    val status = when {
                        saved.alreadyExists -> ContinuousScanStatus.DUPLICATE
                        !savedInvoice.passed -> ContinuousScanStatus.REVIEW
                        else -> ContinuousScanStatus.SAVED
                    }
                    val notice = ContinuousScanNotice(scan, status)
                    mutableContinuousScan.value = mutableContinuousScan.value.let {
                        it.copy(added = it.added + if (saved.alreadyExists) 0 else 1,
                            notice = notice)
                    }
                    mutableContinuousNotices.tryEmit(notice)
                    mutableScanFeedback.tryEmit(Unit)
                } else {
                    showResult(scan)
                    if (saved.alreadyExists) message.value = UiText(R.string.invoice_already_saved)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (currentSession()) {
                if (continuous) {
                    val notice = ContinuousScanNotice(ScanResult(invoice, checkedAt), ContinuousScanStatus.SAVE_FAILED)
                    mutableContinuousScan.value = mutableContinuousScan.value.copy(notice = notice)
                    mutableContinuousNotices.tryEmit(notice)
                }
                else {
                    showResult(ScanResult(invoice, checkedAt))
                    message.value = UiText(R.string.history_save_error)
                }
            }
        }
        return true
    }
    fun open(id: Long) {
        savedState["result_return_screen"] = 2
        loadResult(id, navigate = true)
    }
    fun openStatisticsInvoice(id: Long) {
        savedState["result_return_screen"] = 4
        loadResult(id, navigate = true)
    }
    fun backFromResultOrHome() {
        val destination = savedState.get<Int>("result_return_screen")?.takeIf { it == 2 || it == 4 } ?: 0
        if (screen.value == 1 && destination != 0) {
            // Returning from an invoice keeps the single previous tab for its parent page.
            moveTo(destination)
        } else {
            navigate(previousTab.value ?: 0)
        }
    }
    fun gallery(uri: Uri) { viewModelScope.launch {
        busy.value = true
        try {
            val raw = withContext(Dispatchers.IO) {
                val scanSettings = settings.value
                val bitmap = decodeGallery(getApplication(), uri)
                try { scanSettings.scanEngine.decoder().use { it.read(bitmap, scanSettings) } }
                finally { bitmap.recycle() }
            }
            if (raw == null) message.value = UiText(R.string.gallery_no_qr) else process(raw)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = UiText(R.string.gallery_error) }
        finally { busy.value = false }
    } }
    fun export(uri: Uri, csv: Boolean, filter: HistoryFilter): Job? = startTransfer(TransferKind.EXPORT) {
        val names = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat,
            3 to R.string.export_invoice_date, 4 to R.string.tag_total,
            5 to R.string.tag_tax, 6 to R.string.tag_hash)
            .mapValues { ContextCompat.getString(getApplication(), it.value) }
        outputDocument(uri) { writer ->
            db.withTransaction {
                HistoryExporter.write(db.scans(), filter, writer, csv, names) { count, total ->
                    mutableTransfer.value = DataTransferState(TransferKind.EXPORT, count, total)
                }
            }
        }
        UiText(R.string.export_success)
    }

    fun backup(uri: Uri): Job? = startTransfer(TransferKind.BACKUP) {
        outputDocument(uri, backup = true) { writer ->
            HistoryBackup.write(db, writer) { count, total ->
                mutableTransfer.value = DataTransferState(TransferKind.BACKUP, count, total)
            }
        }
        UiText(R.string.backup_success)
    }

    fun importBackup(uri: Uri): Job? = startTransfer(TransferKind.IMPORT) {
        val cache = File.createTempFile("history-import-", ".json", getApplication<Application>().cacheDir)
        try {
            // Copy cloud/provider input before taking a database transaction; never retain the URI.
            requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use { input ->
                cache.outputStream().use { HistoryBackup.copyLimited(input, it) }
            }
            val summary = cache.reader(Charsets.UTF_8).use { reader ->
                HistoryBackup.restore(db, reader) { count ->
                    mutableTransfer.value = DataTransferState(TransferKind.IMPORT, count)
                }
            }
            UiText(R.string.backup_import_success, summary.added, summary.duplicates)
        } finally {
            cache.delete()
        }
    }

    private fun startTransfer(kind: TransferKind, action: suspend () -> UiText): Job? {
        // Reserve synchronously so two callbacks cannot start overlapping file operations.
        if (mutableTransfer.value != null) return null
        mutableTransfer.value = DataTransferState(kind)
        return viewModelScope.launch {
            try {
                message.value = withContext(Dispatchers.IO) { action() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: BackupTooLargeException) {
                message.value = UiText(R.string.backup_too_large)
            } catch (_: InvalidBackupException) {
                message.value = UiText(R.string.backup_invalid)
            } catch (_: Exception) {
                message.value = UiText(when (kind) {
                    TransferKind.EXPORT -> R.string.export_error
                    TransferKind.BACKUP -> R.string.backup_error
                    TransferKind.IMPORT -> R.string.backup_import_error
                })
            } finally {
                mutableTransfer.value = null
            }
        }
    }

    private suspend fun outputDocument(uri: Uri, backup: Boolean = false, write: suspend (Writer) -> Unit) {
        val cache = File.createTempFile("history-export-", ".json", getApplication<Application>().cacheDir)
        try {
            cache.bufferedWriter(Charsets.UTF_8).use { write(it) }
            if (backup && cache.length() > HistoryBackup.MAX_BYTES) throw BackupTooLargeException()
            mutableTransfer.update { it?.copy(saving = true) }
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use { output ->
                cache.inputStream().use { input ->
                    HistoryBackup.copyLimited(input, output, if (backup) HistoryBackup.MAX_BYTES else Long.MAX_VALUE)
                }
            }
        } finally {
            cache.delete()
        }
    }
    fun clear() { viewModelScope.launch { runCatching { db.scans().clear() }.onFailure { message.value = UiText(R.string.history_delete_error) } } }
    fun keepLatestDay() { viewModelScope.launch {
        runCatching { db.scans().keepLatestDay() }
            .onFailure { message.value = UiText(R.string.history_delete_error) }
    } }
    // Memory only: navigating within the app keeps this choice; leaving the app resets it.
    var skipInvoiceDeleteConfirmation = false
        private set
    fun resetInvoiceDeleteConfirmation() { skipInvoiceDeleteConfirmation = false }
    fun delete(id: Long, skipConfirmationForSession: Boolean = false) {
        if (skipConfirmationForSession) skipInvoiceDeleteConfirmation = true
        viewModelScope.launch {
        runCatching { db.scans().delete(id) }
            .onFailure { message.value = UiText(R.string.history_delete_error) }
        }
    }
    override fun onCleared() { mlKitController.close(); db.close() }
}
