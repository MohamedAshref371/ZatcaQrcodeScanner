package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.Invoice
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.Sink

interface ScannerSavedState {
    fun <T> get(key: String): T?
    operator fun <T> set(key: String, value: T?)
    fun <T> getStateFlow(key: String, initial: T): StateFlow<T>
    fun <T> remove(key: String): T?
}

class MemoryScannerSavedState : ScannerSavedState {
    private val values = mutableMapOf<String, kotlinx.coroutines.flow.MutableStateFlow<Any?>>()
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? = values[key]?.value as T?
    override fun <T> set(key: String, value: T?) { values.getOrPut(key) {
        kotlinx.coroutines.flow.MutableStateFlow(value) }.value = value }
    @Suppress("UNCHECKED_CAST")
    override fun <T> getStateFlow(key: String, initial: T): StateFlow<T> =
        values.getOrPut(key) { kotlinx.coroutines.flow.MutableStateFlow(initial) } as StateFlow<T>
    override fun <T> remove(key: String): T? = get<T>(key).also { set<T>(key, null) }
}

interface StatisticsFilters {
    val hasSavedFilters: Boolean
    fun load(): StatisticsRequest
    fun save(request: StatisticsRequest)
}

enum class InvoiceFileType { IMAGE, PDF }
data class HistoryExportText(val names: Map<Int, String>, val categoryName: (Int) -> String)
data class StatisticsExportText(val headers: List<String>, val summary: String,
    val groupTitle: (StatisticsKey) -> String)

/** Native I/O and formatting only; validation, transactions and duplicate rules live in commonMain. */
interface ScannerFiles {
    fun describe(uri: String): Triple<String, InvoiceFileType?, String>
    suspend fun readImage(uri: String, settings: ScannerSettings): Invoice?
    suspend fun readPdf(uri: String, settings: ScannerSettings, onPage: (Int, Int) -> Unit): List<Invoice>
    suspend fun historyText(): HistoryExportText
    suspend fun statisticsText(request: StatisticsRequest, result: StatisticsResult): StatisticsExportText
    suspend fun outputDocument(uri: String, backup: Boolean, saving: () -> Unit, write: suspend (Sink) -> Unit)
    suspend fun <T> withBackupInput(uri: String, read: suspend (BackupJsonInput) -> T): T
}

class DocumentExportCancelled : Exception()
