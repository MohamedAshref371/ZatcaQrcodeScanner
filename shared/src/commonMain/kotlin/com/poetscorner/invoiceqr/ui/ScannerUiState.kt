package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.ui.text.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class ScanResult(val invoice: Invoice, val checkedAt: Long, val scanId: Long? = null,
    val annotation: InvoiceAnnotation = InvoiceAnnotation())
enum class ImageImportPhase { OPENING, READING_PAGE, SAVING }
data class ImageImportProgress(val current: Int, val total: Int, val fileName: String,
    val phase: ImageImportPhase = ImageImportPhase.OPENING, val page: Int = 0, val pageCount: Int = 0,
    val cancelling: Boolean = false)
data class ImageImportSummary(val added: Int = 0, val duplicates: Int = 0, val needsReview: Int = 0,
    val invalidQr: Int = 0, val unreadable: Int = 0, val saveFailed: Int = 0,
    val interrupted: Boolean = false, val cancelled: Boolean = false,
    val processedFiles: Int = 0, val totalFiles: Int = 0)

interface ScannerUiState {
    val previousTab: StateFlow<Int?>
    val settings: StateFlow<ScannerSettings>
    val historyState: StateFlow<HistoryPageState>
    val result: StateFlow<ScanResult?>
    val busy: StateFlow<Boolean>
    val transfer: StateFlow<DataTransferState?>
    val continuousScan: StateFlow<ContinuousScanState>
    val message: MutableStateFlow<UiText?>
    val screen: StateFlow<Int>
    val restoringResult: StateFlow<Boolean>
    val scanFeedback: Flow<Unit>
    val continuousNotices: Flow<ContinuousScanNotice>
    val imageImport: StateFlow<ImageImportProgress?>
    val imageImportSummary: StateFlow<ImageImportSummary?>
    val statisticsSelection: StateFlow<StatisticsSelection?>
    val statisticsInvoices: StateFlow<HistoryPageState>
    val statisticsGroupBreakdown: StateFlow<StatisticsGroupBreakdownState>
    val statistics: StateFlow<StatisticsState>
    val skipInvoiceDeleteConfirmation: Boolean

    fun recordReviewUse()
    fun refreshStatisticsPeriod()
    fun resetInvoiceDeleteConfirmation()
    fun pauseCameraSession()
    suspend fun claimReviewAttempt(): Boolean
    fun filterHistory(filter: HistoryFilter)
    fun viewContinuousResult(notice: ContinuousScanNotice?)
    fun backFromResultOrHome()
    fun cancelFileImport()
    fun selectTab(screen: Int)
    fun setContinuousScanning(enabled: Boolean)
    suspend fun scanned(raw: String): Boolean
    fun update(change: (ScannerSettings) -> ScannerSettings)
    fun saveAnnotation(annotation: InvoiceAnnotation)
    fun navigate(screen: Int)
    fun inspectBase64(raw: String)
    fun loadMoreHistory()
    fun retryHistory()
    fun open(id: Long)
    fun delete(id: Long, skipConfirmationForSession: Boolean = false)
    fun closeStatisticsGroup()
    fun loadMoreStatisticsInvoices()
    fun retryStatisticsInvoices()
    fun openStatisticsInvoice(id: Long)
    fun retryStatisticsGroupBreakdown()
    fun updateStatistics(request: StatisticsRequest)
    fun retryStatistics()
    suspend fun sellerSuggestions(query: String): List<String>
    fun openStatisticsGroup(selection: StatisticsSelection)
    fun dismissImageImportSummary()
    suspend fun historyDeletionCount(plan: HistoryDeletionPlan): Int
    fun deleteHistory(plan: HistoryDeletionPlan)
}
