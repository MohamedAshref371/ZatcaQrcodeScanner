package com.poetscorner.zatcaqrcodescanner.ui

import android.app.Application
import com.poetscorner.zatcaqrcodescanner.R
import com.poetscorner.zatcaqrcodescanner.ui.text.UiText
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
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

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, ScanDatabase::class.java, "invoices.db").addMigrations(MIGRATION_1_2).build()
    private val repository = SettingsRepository(app)
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ScannerSettings())
    val historyEntries = db.scans().observe().map { rows -> rows.map { HistoryEntry.from(it) } }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val result = MutableStateFlow<Invoice?>(null)
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
        result.value = invoice
        try { db.scans().insert(ScanEntity(raw = raw, seller = invoice.value(1), passed = invoice.passed, phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name)) }
        catch (_: Exception) { message.value = UiText(R.string.history_save_error) }
        return true
    }
    fun open(scan: ScanEntity) { viewModelScope.launch { result.value = withContext(Dispatchers.Default) { ZatcaParser.parse(scan.raw) } } }
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
    fun export(uri: Uri, csv: Boolean) { viewModelScope.launch {
        try { withContext(Dispatchers.IO) {
            val rows = db.scans().all()
            val names = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat,
                3 to R.string.export_invoice_date, 4 to R.string.tag_total,
                5 to R.string.tag_tax, 6 to R.string.tag_hash)
                .mapValues { getApplication<Application>().getString(it.value) }
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
                it.write(if (csv) HistoryExporter.csv(rows, names) else HistoryExporter.json(rows, names))
            }
        }; message.value = UiText(R.string.export_success) }
        catch (_: Exception) { message.value = UiText(R.string.export_error) }
    } }
    fun clear() { viewModelScope.launch { runCatching { db.scans().clear() }.onFailure { message.value = UiText(R.string.history_delete_error) } } }
    fun keepLatestDay() { viewModelScope.launch {
        runCatching { db.scans().keepLatestDay() }
            .onFailure { message.value = UiText(R.string.history_delete_error) }
    } }
    fun delete(scan: ScanEntity) { viewModelScope.launch {
        runCatching { db.scans().delete(scan.id) }
            .onFailure { message.value = UiText(R.string.history_delete_error) }
    } }
    override fun onCleared() { db.close() }
}
