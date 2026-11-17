package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.backup_error
import com.poetscorner.invoiceqr.resources.backup_import_error
import com.poetscorner.invoiceqr.resources.backup_import_success
import com.poetscorner.invoiceqr.resources.backup_invalid
import com.poetscorner.invoiceqr.resources.backup_success
import com.poetscorner.invoiceqr.resources.backup_too_large
import com.poetscorner.invoiceqr.resources.export_error
import com.poetscorner.invoiceqr.resources.export_success
import com.poetscorner.invoiceqr.resources.gallery_error
import com.poetscorner.invoiceqr.resources.gallery_no_qr
import com.poetscorner.invoiceqr.resources.history_delete_error
import com.poetscorner.invoiceqr.resources.history_entry_missing
import com.poetscorner.invoiceqr.resources.history_load_error
import com.poetscorner.invoiceqr.resources.history_save_error
import com.poetscorner.invoiceqr.resources.image_import_busy
import com.poetscorner.invoiceqr.resources.import_unsupported
import com.poetscorner.invoiceqr.resources.invoice_already_saved
import com.poetscorner.invoiceqr.resources.invoice_details_save_error
import com.poetscorner.invoiceqr.resources.scan_invalid
import com.poetscorner.invoiceqr.resources.stats_export_success
import android.app.Application
import androidx.core.content.ContextCompat
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.InvoiceFileType
import com.poetscorner.invoiceqr.invoiceFileDisplayName
import com.poetscorner.invoiceqr.invoiceFileType
import com.poetscorner.invoiceqr.ui.text.UiText
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.*
import com.poetscorner.invoiceqr.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered

class ScannerViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app), ScannerUiState {
    private val db = openScanDatabase(app)
    private val repository = SettingsRepository(app)
    private val reviewRepository = ReviewRepository(app)
    override fun recordReviewUse() { viewModelScope.launch {
        try { reviewRepository.recordUse() } catch (_: java.io.IOException) { /* Optional tracking. */ }
    } }
    override suspend fun claimReviewAttempt(): Boolean = try {
        reviewRepository.claimAttempt()
    } catch (_: java.io.IOException) { false }
    private val mutableSettings = MutableStateFlow(ScannerSettings())
    override val settings = mutableSettings.asStateFlow()
    private val mutableSettingsLoaded = MutableStateFlow(false)
    val settingsLoaded = mutableSettingsLoaded.asStateFlow()
    init {
        viewModelScope.launch {
            repository.settings.collect { loaded ->
                mutableSettings.value = loaded
                mutableSettingsLoaded.value = true
            }
        }
    }
    private val historyPager = HistoryPager(db.scans(), viewModelScope)
    override val historyState = historyPager.state
    override fun filterHistory(filter: HistoryFilter) = historyPager.setFilter(filter)
    override fun loadMoreHistory() = historyPager.loadMore()
    override fun retryHistory() = historyPager.refresh()
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
    override val statistics = combine(statisticsRequest, statisticsRevision) { request, _ -> request }
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
    override fun updateStatistics(request: StatisticsRequest) {
        val mode = if (request.excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else request.vatMode
        val resolved = request.withVatFilter(mode, if (mode == StatisticsVatMode.EXCLUDE) request.excludedVat else request.vat).resolvePeriod()
        statisticsFilters.save(resolved)
        savedState["statistics_request"] = resolved
    }
    override fun refreshStatisticsPeriod() {
        val request = statisticsRequest.value
        val resolved = request.resolvePeriod()
        if (request != resolved) savedState["statistics_request"] = resolved
    }
    override fun retryStatistics() { statisticsRevision.value++ }
    override suspend fun sellerSuggestions(query: String): List<String> =
        db.scans().sellerSuggestions(query.trim().lowercase(java.util.Locale.ROOT))
    override val statisticsSelection = savedState.getStateFlow<StatisticsSelection?>("statistics_group", null)
    private val statisticsBreakdownRevision = MutableStateFlow(0L)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val statisticsGroupBreakdown = combine(statisticsSelection, statisticsBreakdownRevision) { selection, _ -> selection }
        .flatMapLatest { selection ->
            if (selection == null || !selection.request.categoryCalculationActive)
                flowOf(StatisticsGroupBreakdownState())
            else db.scans().observeCounts(HistoryQueries(HistoryFilter()).counts()).transformLatest {
                emit(StatisticsGroupBreakdownState(selection, loading = true))
                val result = withContext(Dispatchers.IO) { db.statisticsBreakdown(selection) }
                emit(StatisticsGroupBreakdownState(selection, result))
            }.onStart { emit(StatisticsGroupBreakdownState(selection, loading = true)) }
                .catch { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    emit(StatisticsGroupBreakdownState(selection, failed = true))
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsGroupBreakdownState())
    override fun retryStatisticsGroupBreakdown() { statisticsBreakdownRevision.value++ }
    private val statisticsGroupPager by lazy {
        HistoryPager(db.scans(), viewModelScope).also { pager ->
            statisticsSelection.value?.let { pager.setFilter(HistoryFilter(statisticsSelection = it)) }
        }
    }
    override val statisticsInvoices get() = statisticsGroupPager.state
    override fun openStatisticsGroup(selection: StatisticsSelection) {
        savedState["statistics_group"] = selection
        statisticsGroupPager.setFilter(HistoryFilter(statisticsSelection = selection))
    }
    override fun closeStatisticsGroup() { savedState["statistics_group"] = null }
    override fun loadMoreStatisticsInvoices() = statisticsGroupPager.loadMore()
    override fun retryStatisticsInvoices() = statisticsGroupPager.refresh()
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
            val net = request.categoryCalculationActive
            val headers = listOf(request.grouping.androidLabel(), R.string.stats_count_label,
                if (net) {
                    if (result.summary.missingTotal > 0) R.string.stats_available_net_total else R.string.stats_net_total
                } else if (result.summary.missingTotal > 0) R.string.stats_available_total else R.string.stats_total_including_tax,
                if (net) {
                    if (result.summary.missingTax > 0) R.string.stats_available_net_tax else R.string.stats_net_tax
                } else if (result.summary.missingTax > 0) R.string.stats_available_tax else R.string.tag_tax,
                R.string.stats_missing_total_count, R.string.stats_missing_tax_count).map(context::getString)
            outputDocument(uri) { writer ->
                StatisticsExporter.write(writer, result, headers, context.getString(R.string.stats_summary)) {
                    statisticsGroupTitle(context, request, it)
                }
            }
            UiText(Res.string.stats_export_success)
        }
        if (job != null) cancelStatisticsExport()
        return job
    }
    private val mutableResult = MutableStateFlow<ScanResult?>(null)
    override val result = mutableResult.asStateFlow()
    override val screen = savedState.getStateFlow("screen", 0)
    override val previousTab = savedState.getStateFlow<Int?>("previous_tab", null)
    private val mutableRestoringResult = MutableStateFlow(false)
    override val restoringResult = mutableRestoringResult.asStateFlow()
    private var resultLoad: Job? = null
    private var resultVersion = 0L
    override val message = MutableStateFlow<UiText?>(null)
    override val busy = MutableStateFlow(false)
    private val mutableImageImport = MutableStateFlow<ImageImportProgress?>(null)
    override val imageImport = mutableImageImport.asStateFlow()
    private var fileImportJob: Job? = null
    override fun cancelFileImport() {
        val job = fileImportJob ?: return
        if (!job.isActive) return
        mutableImageImport.update { it?.copy(cancelling = true) }
        job.cancel()
    }
    private val mutableImageImportSummary = MutableStateFlow<ImageImportSummary?>(null)
    override val imageImportSummary = mutableImageImportSummary.asStateFlow()
    override fun dismissImageImportSummary() { mutableImageImportSummary.value = null }
    private val mutableTransfer = MutableStateFlow<DataTransferState?>(null)
    override val transfer = mutableTransfer.asStateFlow()
    init {
        savedState.get<Long>("result_scan_id")?.let { loadResult(it, navigate = false) }
    }

    override fun navigate(screen: Int) {
        savedState["previous_tab"] = null
        moveTo(screen)
    }

    override fun selectTab(screen: Int) {
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
                    message.value = UiText(Res.string.history_entry_missing)
                } else {
                    mutableResult.value = withContext(Dispatchers.Default) {
                        ScanResult(ZatcaParser.parse(scan.raw), scan.scannedAt, scan.id, InvoiceAnnotation.from(scan))
                    }
                    if (navigate) savedState["screen"] = 1
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                message.value = UiText(Res.string.history_load_error)
            } finally {
                if (version == resultVersion) mutableRestoringResult.value = false
            }
        }
    }
    private val mutableContinuousScan = MutableStateFlow(ContinuousScanState())
    override val continuousScan = mutableContinuousScan.asStateFlow()
    private val mutableScanFeedback = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val scanFeedback = mutableScanFeedback.asSharedFlow()
    private val mutableContinuousNotices = MutableSharedFlow<ContinuousScanNotice>(extraBufferCapacity = 1)
    override val continuousNotices = mutableContinuousNotices.asSharedFlow()
    private var cameraSession = 0L
    override fun setContinuousScanning(enabled: Boolean) {
        if (mutableContinuousScan.value.enabled == enabled) return
        cameraSession++
        mutableContinuousScan.value = mutableContinuousScan.value.copy(enabled = enabled, notice = null)
    }
    fun resetCameraSession() {
        cameraSession++
        mutableContinuousScan.value = ContinuousScanState()
    }
    override fun pauseCameraSession() {
        // Ignore in-flight callbacks, keeping the session choice and counter through screen lock.
        cameraSession++
        mutableContinuousScan.value = mutableContinuousScan.value.copy(notice = null)
    }
    fun viewContinuousResult() = viewContinuousResult(mutableContinuousScan.value.notice)

    override fun viewContinuousResult(notice: ContinuousScanNotice?) {
        notice?.let { showResult(it.result) }
    }
    private val settingsMutex = Mutex()
    override fun update(change: (ScannerSettings) -> ScannerSettings) { viewModelScope.launch {
        settingsMutex.withLock { repository.save(change(repository.settings.first())) }
    } }
    // Keep saving an accepted scan even when showing its result disposes the camera.
    override suspend fun scanned(raw: String): Boolean {
        val session = cameraSession
        val continuous = mutableContinuousScan.value.enabled
        return viewModelScope.async { process(raw, session, continuous) }.await()
    }
    override fun inspectBase64(raw: String) {
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
            if (currentSession()) message.value = UiText(Res.string.scan_invalid)
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
                val scan = ScanResult(savedInvoice, saved.scan.scannedAt, saved.scan.id,
                    InvoiceAnnotation.from(saved.scan))
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
                    if (saved.alreadyExists) message.value = UiText(Res.string.invoice_already_saved)
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
                    message.value = UiText(Res.string.history_save_error)
                }
            }
        }
        return true
    }
    override fun open(id: Long) {
        savedState["result_return_screen"] = 2
        loadResult(id, navigate = true)
    }
    override fun openStatisticsInvoice(id: Long) {
        savedState["result_return_screen"] = 4
        loadResult(id, navigate = true)
    }
    override fun saveAnnotation(annotation: InvoiceAnnotation) {
        val current = mutableResult.value ?: return
        val id = current.scanId ?: return
        if (annotation.invoiceNumber.length > 120 || annotation.note.length > 2_000 ||
            annotation.category !in InvoiceCategories.ids) return
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    db.scans().updateAnnotation(id, annotation.invoiceNumber, annotation.category, annotation.note)
                }
                if (updated == 0) {
                    message.value = UiText(Res.string.history_entry_missing)
                } else if (mutableResult.value?.scanId == id) {
                    mutableResult.value = mutableResult.value?.copy(annotation = annotation)
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                message.value = UiText(Res.string.invoice_details_save_error)
            }
        }
    }
    override fun backFromResultOrHome() {
        val destination = savedState.get<Int>("result_return_screen")?.takeIf { it == 2 || it == 4 } ?: 0
        if (screen.value == 1 && destination != 0) {
            // Returning from an invoice keeps the single previous tab for its parent page.
            moveTo(destination)
        } else {
            navigate(previousTab.value ?: 0)
        }
    }
    fun gallery(uri: Uri) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                settingsLoaded.first { it }
                val raw = withContext(Dispatchers.IO) {
                    val scanSettings = settings.value
                    val bitmap = decodeGallery(getApplication(), uri)
                    try { ZxingDecoder().use { it.readImportedImage(bitmap, scanSettings) } }
                    finally { bitmap.recycle() }
                }
                if (raw == null) message.value = UiText(Res.string.gallery_no_qr) else process(raw)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = UiText(Res.string.gallery_error) }
            finally { busy.value = false }
        }
    }
    fun importInvoiceFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (busy.value) { message.value = UiText(Res.string.image_import_busy); return }
        busy.value = true
        mutableImageImportSummary.value = null
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val app = getApplication<Application>()
            val files = try {
                withContext(Dispatchers.IO) {
                    uris.distinct().map { uri -> Triple(uri, app.invoiceFileType(uri), app.invoiceFileDisplayName(uri)) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                busy.value = false
                throw cancelled
            } catch (_: Exception) {
                busy.value = false
                message.value = UiText(Res.string.import_unsupported)
                return@launch
            }
            if (files.all { it.second == null }) {
                busy.value = false
                message.value = UiText(Res.string.import_unsupported)
                return@launch
            }
            if (files.size == 1 && files.single().second == InvoiceFileType.IMAGE) {
                busy.value = false
                gallery(files.single().first)
                return@launch
            }
            mutableImageImport.value = ImageImportProgress(1, files.size, files.first().third)
            var summary = ImageImportSummary(totalFiles = files.size)
            val seenInImport = mutableSetOf<String>()
            try {
                settingsLoaded.first { it }
                val scanSettings = settings.value
                withContext(Dispatchers.IO) {
                    ZxingDecoder().use { decoder ->
                        files.forEachIndexed { index, (uri, type, fileName) ->
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            mutableImageImport.value = ImageImportProgress(index + 1, files.size, fileName)
                            if (type == null) {
                                summary = summary.copy(unreadable = summary.unreadable + 1, processedFiles = index + 1)
                                return@forEachIndexed
                            }
                            val invoices = try {
                                when (type) {
                                    InvoiceFileType.IMAGE -> {
                                        val bitmap = decodeGallery(app, uri)
                                        val raw = try { decoder.readImportedImage(bitmap, scanSettings) } finally { bitmap.recycle() }
                                        raw?.let { listOf(ZatcaParser.parse(it)) }.orEmpty()
                                    }
                                    InvoiceFileType.PDF -> invoicesInPdf(app, uri, onPage = { page, count ->
                                        mutableImageImport.update { it?.copy(phase = ImageImportPhase.READING_PAGE,
                                            page = page, pageCount = count) }
                                    }) { bitmap ->
                                        decoder.readAll(bitmap, scanSettings)
                                    }
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                summary = summary.copy(unreadable = summary.unreadable + 1, processedFiles = index + 1)
                                return@forEachIndexed
                            }
                            val validInvoices = invoices.filter { it.error == null }
                            if (validInvoices.isEmpty()) {
                                summary = summary.copy(invalidQr = summary.invalidQr + 1, processedFiles = index + 1)
                                return@forEachIndexed
                            }
                            // Repeated QR codes in this batch do not count as invoices already in history.
                            val pending = validInvoices.map { invoicePayloadKey(it.raw) to it }
                                .distinctBy { it.first }.filterNot { it.first in seenInImport }
                            mutableImageImport.update { it?.copy(phase = ImageImportPhase.SAVING, page = 0, pageCount = 0) }
                            val saved = try {
                                db.writeTransaction {
                                    pending.map { (key, invoice) ->
                                        key to db.scans().saveIfNew(ScanEntity.from(invoice, System.currentTimeMillis()))
                                    }
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                summary = summary.copy(saveFailed = summary.saveFailed + pending.size,
                                    processedFiles = index + 1)
                                return@forEachIndexed
                            }
                            seenInImport.addAll(saved.map { it.first })
                            summary = summary.copy(
                                added = summary.added + saved.count { !it.second.alreadyExists },
                                duplicates = summary.duplicates + saved.count { it.second.alreadyExists },
                                needsReview = summary.needsReview + saved.count {
                                    !it.second.alreadyExists && !it.second.scan.passed
                                }, processedFiles = index + 1)
                            for ((_, result) in saved) {
                                if (!result.alreadyExists) try {
                                    reviewRepository.recordSavedInvoice(result.scan.payloadKey)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) { /* Optional review tracking. */ }
                            }
                        }
                    }
                }
                mutableImageImportSummary.value = summary
                navigate(2)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (mutableImageImport.value?.cancelling != true) throw cancelled
                mutableImageImportSummary.value = if (summary.processedFiles == files.size) summary
                    else summary.copy(interrupted = true, cancelled = true)
                navigate(2)
            } catch (_: Exception) {
                mutableImageImportSummary.value = summary.copy(interrupted = true)
                navigate(2)
            } finally {
                mutableImageImport.value = null
                busy.value = false
            }
        }
        fileImportJob = job
        job.invokeOnCompletion { if (fileImportJob === job) fileImportJob = null }
        job.start()
    }
    fun export(uri: Uri, csv: Boolean, filter: HistoryFilter): Job? = startTransfer(TransferKind.EXPORT) {
        val names = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat,
            3 to R.string.export_invoice_date, 4 to R.string.tag_total,
            5 to R.string.tag_tax, 6 to R.string.tag_hash,
            7 to R.string.invoice_number, 8 to R.string.invoice_category, 9 to R.string.invoice_note)
            .mapValues { ContextCompat.getString(getApplication(), it.value) }
        val languageContext = ContextCompat.getContextForLanguage(getApplication())
        outputDocument(uri) { writer ->
            db.readSnapshot {
                HistoryExporter.write(db.scans(), filter, writer, csv, names,
                    categoryName = { languageContext.getString(InvoiceCategories.label(it)) }) { count, total ->
                    mutableTransfer.value = DataTransferState(TransferKind.EXPORT, count, total)
                }
            }
        }
        UiText(Res.string.export_success)
    }

    fun backup(uri: Uri): Job? = startTransfer(TransferKind.BACKUP) {
        outputDocument(uri, backup = true) { writer ->
            HistoryBackup.write(db, writer) { count, total ->
                mutableTransfer.value = DataTransferState(TransferKind.BACKUP, count, total)
            }
        }
        UiText(Res.string.backup_success)
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
            UiText(Res.string.backup_import_success, summary.added, summary.duplicates)
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
                message.value = UiText(Res.string.backup_too_large)
            } catch (_: InvalidBackupException) {
                message.value = UiText(Res.string.backup_invalid)
            } catch (_: Exception) {
                message.value = UiText(when (kind) {
                    TransferKind.EXPORT -> Res.string.export_error
                    TransferKind.BACKUP -> Res.string.backup_error
                    TransferKind.IMPORT -> Res.string.backup_import_error
                })
            } finally {
                mutableTransfer.value = null
            }
        }
    }

    private suspend fun outputDocument(uri: Uri, backup: Boolean = false, write: suspend (Sink) -> Unit) {
        val cache = File.createTempFile("history-export-", ".json", getApplication<Application>().cacheDir)
        try {
            cache.outputStream().asSink().buffered().use { write(it) }
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
    override suspend fun historyDeletionCount(plan: HistoryDeletionPlan): Int = withContext(Dispatchers.IO) {
        db.scans().deletionCount(plan)
    }
    override fun deleteHistory(plan: HistoryDeletionPlan) { viewModelScope.launch {
        runCatching { db.scans().deleteSelection(plan) }
            .onFailure { message.value = UiText(Res.string.history_delete_error) }
    } }
    // Memory only: navigating within the app keeps this choice; leaving the app resets it.
    override var skipInvoiceDeleteConfirmation = false
        private set
    override fun resetInvoiceDeleteConfirmation() { skipInvoiceDeleteConfirmation = false }
    override fun delete(id: Long, skipConfirmationForSession: Boolean) {
        if (skipConfirmationForSession) skipInvoiceDeleteConfirmation = true
        viewModelScope.launch {
        runCatching { db.scans().delete(id) }
            .onFailure { message.value = UiText(Res.string.history_delete_error) }
        }
    }
    override fun onCleared() { db.close() }
}
