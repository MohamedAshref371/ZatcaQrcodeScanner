package com.poetscorner.zatcaqrcodescanner.ui

import android.app.Application
import com.poetscorner.zatcaqrcodescanner.R
import com.poetscorner.zatcaqrcodescanner.ui.text.UiText
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.poetscorner.zatcaqrcodescanner.data.*
import com.poetscorner.zatcaqrcodescanner.domain.*
import com.poetscorner.zatcaqrcodescanner.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScanResult(val invoice: Invoice, val checkedAt: Long)

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, ScanDatabase::class.java, "invoices.db")
        .fallbackToDestructiveMigration(dropAllTables = true).build()
    private val repository = SettingsRepository(app)
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ScannerSettings())
    private val historyPager = HistoryPager(db.scans(), viewModelScope)
    val historyState = historyPager.state
    fun filterHistory(filter: HistoryFilter) = historyPager.setFilter(filter)
    fun loadMoreHistory() = historyPager.loadMore()
    fun retryHistory() = historyPager.refresh()
    val result = MutableStateFlow<ScanResult?>(null)
    val message = MutableStateFlow<UiText?>(null)
    val busy = MutableStateFlow(false)
    private val settingsMutex = Mutex()
    fun update(change: (ScannerSettings) -> ScannerSettings) { viewModelScope.launch {
        settingsMutex.withLock { repository.save(change(repository.settings.first())) }
    } }
    // Keep saving an accepted scan even when showing its result disposes the camera.
    suspend fun scanned(raw: String): Boolean = viewModelScope.async { process(raw) }.await()
    fun inspectBase64(raw: String) {
        if (busy.value || raw.isBlank()) return
        busy.value = true
        viewModelScope.launch {
            try { process(raw) }
            finally { busy.value = false }
        }
    }
    private suspend fun process(raw: String): Boolean {
        val invoice = withContext(Dispatchers.Default) { ZatcaParser.parse(raw) }
        if (invoice.error != null) {
            message.value = UiText(R.string.scan_invalid)
            return false
        }
        val checkedAt = System.currentTimeMillis()
        try {
            val saved = withContext(Dispatchers.Default) { db.scans().saveIfNew(ScanEntity.from(invoice, checkedAt)) }
            val savedInvoice = if (saved.alreadyExists) withContext(Dispatchers.Default) {
                ZatcaParser.parse(saved.scan.raw)
            } else invoice
            result.value = ScanResult(savedInvoice, saved.scan.scannedAt)
            if (saved.alreadyExists) message.value = UiText(R.string.invoice_already_saved)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            result.value = ScanResult(invoice, checkedAt)
            message.value = UiText(R.string.history_save_error)
        }
        return true
    }
    fun open(id: Long) {
        result.value = null
        viewModelScope.launch {
            try {
                val scan = db.scans().byId(id)
                if (scan == null) message.value = UiText(R.string.history_entry_missing)
                else result.value = withContext(Dispatchers.Default) { ScanResult(ZatcaParser.parse(scan.raw), scan.scannedAt) }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                message.value = UiText(R.string.history_load_error)
            }
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
        } catch (_: Exception) { message.value = UiText(R.string.gallery_error) }
        finally { busy.value = false }
    } }
    fun export(uri: Uri, csv: Boolean, filter: HistoryFilter) { viewModelScope.launch {
        try { withContext(Dispatchers.IO) {
            val names = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat,
                3 to R.string.export_invoice_date, 4 to R.string.tag_total,
                5 to R.string.tag_tax, 6 to R.string.tag_hash)
                .mapValues { getApplication<Application>().getString(it.value) }
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
                db.withTransaction { HistoryExporter.write(db.scans(), filter, it, csv, names) }
            }
        }; message.value = UiText(R.string.export_success) }
        catch (_: Exception) { message.value = UiText(R.string.export_error) }
    } }
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
    override fun onCleared() { db.close() }
}
