package com.poetscorner.invoiceqr.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.flow.StateFlow

private class AndroidScannerState(private val handle: SavedStateHandle) : ScannerSavedState {
    override fun <T> get(key: String): T? = handle[key]
    override fun <T> set(key: String, value: T?) { handle[key] = value }
    override fun <T> getStateFlow(key: String, initial: T): StateFlow<T> = handle.getStateFlow(key, initial)
    override fun <T> remove(key: String): T? = handle.remove(key)
}

class ScannerViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app), ScannerUiState {
    private val db = openScanDatabase(app)
    private val controller = ScannerController(db, SettingsRepository(app), ReviewRepository(app),
        StatisticsFilterRepository(app), AndroidScannerState(savedState), AndroidScannerFiles(app), viewModelScope)
    override val previousTab get() = controller.previousTab
    override val settings get() = controller.settings
    override val historyState get() = controller.historyState
    override val result get() = controller.result
    override val busy get() = controller.busy
    override val transfer get() = controller.transfer
    override val continuousScan get() = controller.continuousScan
    override val message get() = controller.message
    override val screen get() = controller.screen
    override val restoringResult get() = controller.restoringResult
    override val scanFeedback get() = controller.scanFeedback
    override val continuousNotices get() = controller.continuousNotices
    override val imageImport get() = controller.imageImport
    override val imageImportSummary get() = controller.imageImportSummary
    override val statisticsSelection get() = controller.statisticsSelection
    override val statisticsInvoices get() = controller.statisticsInvoices
    override val statisticsGroupBreakdown get() = controller.statisticsGroupBreakdown
    override val statistics get() = controller.statistics
    override val skipInvoiceDeleteConfirmation get() = controller.skipInvoiceDeleteConfirmation
    override fun recordReviewUse() = controller.recordReviewUse()
    override fun refreshStatisticsPeriod() = controller.refreshStatisticsPeriod()
    override fun resetInvoiceDeleteConfirmation() = controller.resetInvoiceDeleteConfirmation()
    override fun pauseCameraSession() = controller.pauseCameraSession()
    override suspend fun claimReviewAttempt() = controller.claimReviewAttempt()
    override fun filterHistory(filter: HistoryFilter) = controller.filterHistory(filter)
    override fun viewContinuousResult(notice: ContinuousScanNotice?) = controller.viewContinuousResult(notice)
    override fun backFromResultOrHome() = controller.backFromResultOrHome()
    override fun cancelFileImport() = controller.cancelFileImport()
    override fun selectTab(screen: Int) = controller.selectTab(screen)
    override fun setContinuousScanning(enabled: Boolean) = controller.setContinuousScanning(enabled)
    override suspend fun scanned(raw: String) = controller.scanned(raw)
    override fun update(change: (ScannerSettings) -> ScannerSettings) = controller.update(change)
    override fun saveAnnotation(annotation: InvoiceAnnotation) = controller.saveAnnotation(annotation)
    override fun navigate(screen: Int) = controller.navigate(screen)
    override fun inspectBase64(raw: String) = controller.inspectBase64(raw)
    override fun loadMoreHistory() = controller.loadMoreHistory()
    override fun retryHistory() = controller.retryHistory()
    override fun open(id: Long) = controller.open(id)
    override fun delete(id: Long, skipConfirmationForSession: Boolean) = controller.delete(id, skipConfirmationForSession)
    override fun closeStatisticsGroup() = controller.closeStatisticsGroup()
    override fun loadMoreStatisticsInvoices() = controller.loadMoreStatisticsInvoices()
    override fun retryStatisticsInvoices() = controller.retryStatisticsInvoices()
    override fun openStatisticsInvoice(id: Long) = controller.openStatisticsInvoice(id)
    override fun retryStatisticsGroupBreakdown() = controller.retryStatisticsGroupBreakdown()
    override fun updateStatistics(request: StatisticsRequest) = controller.updateStatistics(request)
    override fun retryStatistics() = controller.retryStatistics()
    override suspend fun sellerSuggestions(query: String) = controller.sellerSuggestions(query)
    override fun openStatisticsGroup(selection: StatisticsSelection) = controller.openStatisticsGroup(selection)
    override fun dismissImageImportSummary() = controller.dismissImageImportSummary()
    override suspend fun historyDeletionCount(plan: HistoryDeletionPlan) = controller.historyDeletionCount(plan)
    override fun deleteHistory(plan: HistoryDeletionPlan) = controller.deleteHistory(plan)

    val settingsLoaded get() = controller.settingsLoaded
    fun clearResult() = controller.clearResult()
    fun resetCameraSession() = controller.resetCameraSession()
    fun viewContinuousResult() = controller.viewContinuousResult()
    fun gallery(uri: Uri) = controller.gallery(uri.toString())
    fun importInvoiceFiles(uris: List<Uri>) = controller.importInvoiceFiles(uris.map(Uri::toString))
    fun prepareStatisticsExport(state: StatisticsState) = controller.prepareStatisticsExport(state)
    fun cancelStatisticsExport() = controller.cancelStatisticsExport()
    fun exportStatistics(uri: Uri) = controller.exportStatistics(uri.toString())
    fun export(uri: Uri, csv: Boolean, filter: HistoryFilter) = controller.export(uri.toString(), csv, filter)
    fun backup(uri: Uri) = controller.backup(uri.toString())
    fun importBackup(uri: Uri) = controller.importBackup(uri.toString())
    override fun onCleared() { db.close() }
}
