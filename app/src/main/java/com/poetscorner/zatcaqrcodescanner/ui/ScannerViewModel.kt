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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, ScanDatabase::class.java, "invoices.db").addMigrations(MIGRATION_1_2).build()
    private val repository = SettingsRepository(app)
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ScannerSettings())
    val history = db.scans().observe().map { rows -> rows.map { it.revalidated() } }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val result = MutableStateFlow<Invoice?>(null)
    val message = MutableStateFlow<UiText?>(null)
    val busy = MutableStateFlow(false)
    private val settingsMutex = Mutex()
    fun update(change: (ScannerSettings) -> ScannerSettings) { viewModelScope.launch {
        settingsMutex.withLock { repository.save(change(repository.settings.first())) }
    } }
    fun scanned(raw: String) { viewModelScope.launch { process(raw) } }
    private suspend fun process(raw: String) {
        val invoice = withContext(Dispatchers.Default) { ZatcaParser.parse(raw) }
        result.value = invoice
        try { db.scans().insert(ScanEntity(raw = raw, seller = invoice.value(1), passed = invoice.passed, phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name)) }
        catch (_: Exception) { message.value = UiText(R.string.history_save_error) }
    }
    fun open(scan: ScanEntity) { viewModelScope.launch { result.value = withContext(Dispatchers.Default) { ZatcaParser.parse(scan.raw) } } }
    fun gallery(uri: Uri) { viewModelScope.launch {
        busy.value = true
        try {
            val raw = withContext(Dispatchers.IO) {
                val bitmap = decodeGallery(getApplication(), uri)
                try { settings.value.reader().read(bitmap).firstOrNull { it.error == null }?.text }
                finally { bitmap.recycle() }
            }
            if (raw == null) message.value = UiText(R.string.gallery_no_qr) else process(raw)
        } catch (_: Exception) { message.value = UiText(R.string.gallery_error) }
        finally { busy.value = false }
    } }
    fun export(uri: Uri, csv: Boolean) { viewModelScope.launch {
        try { withContext(Dispatchers.IO) {
            val rows = db.scans().all()
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
                it.write(if (csv) HistoryExporter.csv(rows) else HistoryExporter.json(rows, ZatcaParser.names.mapValues { getApplication<Application>().getString(it.value) }))
            }
        }; message.value = UiText(R.string.export_success) }
        catch (_: Exception) { message.value = UiText(R.string.export_error) }
    } }
    fun clear() { viewModelScope.launch { runCatching { db.scans().clear() }.onFailure { message.value = UiText(R.string.history_delete_error) } } }
    override fun onCleared() { db.close() }
}
