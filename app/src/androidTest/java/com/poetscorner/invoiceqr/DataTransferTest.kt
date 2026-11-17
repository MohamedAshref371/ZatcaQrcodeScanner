package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.openScanDatabase
import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import com.poetscorner.invoiceqr.ui.TransferKind
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataTransferTest {
    @Test fun operationsAreExclusiveExportRespectsFiltersAndFailedImportsCanBeRetried() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val store = ViewModelStore()
        val file = File.createTempFile("transfer-test-", ".json", app.cacheDir)
        var vm: ScannerViewModel? = null
        var savedId: Long? = null
        try {
            withContext(Dispatchers.Main) {
                val model = ViewModelProvider(store, viewModelFactory {
                    initializer { ScannerViewModel(app, SavedStateHandle()) }
                })[ScannerViewModel::class.java]
                vm = model
                val seller = "Transfer seller ${System.nanoTime()}"
                model.scanned(historyFixture(99001, seller = seller, hash = false).raw)
                val originalTime = model.result.value!!.checkedAt
                savedId = model.result.value!!.scanId
                val export = requireNotNull(model.export(Uri.fromFile(file), false, HistoryFilter(seller)))
                assertEquals(TransferKind.EXPORT, model.transfer.value?.kind)
                assertNull(model.backup(Uri.fromFile(file)))
                assertNull(model.importBackup(Uri.fromFile(file)))
                export.join()
                assertNull(model.transfer.value)
                assertEquals(R.string.export_success, model.message.value?.id)
                assertEquals(1, JSONArray(file.readText()).length())

                val missingDestination = File(app.cacheDir, "missing-${System.nanoTime()}/export.csv")
                requireNotNull(model.export(Uri.fromFile(missingDestination), true, HistoryFilter(seller))).join()
                assertEquals(R.string.export_error, model.message.value?.id)
                assertNull(model.transfer.value)

                file.writeText("not a backup")
                requireNotNull(model.importBackup(Uri.fromFile(file))).join()
                assertEquals(R.string.backup_invalid, model.message.value?.id)
                assertNull(model.transfer.value)

                requireNotNull(model.backup(Uri.fromFile(file))).join()
                assertEquals(R.string.backup_success, model.message.value?.id)
                val rows = JSONObject(file.readText()).getJSONArray("invoices")
                assertTrue(rows.length() >= 1)
                val original = (0 until rows.length()).map { rows.getJSONObject(it) }
                    .first { it.getString("raw") == model.result.value!!.invoice.raw }
                assertEquals(originalTime, original.getLong("checkedAt"))
                requireNotNull(model.importBackup(Uri.fromFile(file))).join()
                assertEquals(R.string.backup_import_success, model.message.value?.id)
                assertEquals(0, model.message.value?.args?.first())
                assertNull(model.transfer.value)

                val cancel = requireNotNull(model.export(Uri.fromFile(file), true, HistoryFilter()))
                cancel.cancelAndJoin()
                assertNull(model.transfer.value)
                assertNotEquals(R.string.export_error, model.message.value?.id)
            }
        } finally {
            withContext(Dispatchers.Main) {
                val job = vm?.viewModelScope?.coroutineContext?.get(Job)
                store.clear()
                job?.join()
            }
            val cleanup = openScanDatabase(app)
            try { savedId?.let { cleanup.scans().delete(it) } } finally { cleanup.close() }
            file.delete()
        }
    }
}
