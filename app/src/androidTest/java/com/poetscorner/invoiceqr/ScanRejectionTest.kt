package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.openScanDatabase
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanRejectionTest {
    @Test fun malformedQrNeverPublishesAResultOrChangesHistory() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val db = openScanDatabase(app, "invoices.db")
        val store = ViewModelStore()
        try {
            val before = db.scans().all()
            withContext(Dispatchers.Main) {
                val vm = ViewModelProvider(store, viewModelFactory {
                    initializer { ScannerViewModel(app, SavedStateHandle()) }
                })[ScannerViewModel::class.java]
                listOf("https://example.org", "%%%", "", "aGVsbG8=", "AQVB").forEach { raw ->
                    assertFalse(raw, vm.scanned(raw))
                    assertNull(vm.result.value)
                    assertEquals(R.string.scan_invalid, vm.message.value?.id)
                    vm.message.value = null
                }
            }
            assertEquals(before, db.scans().all())
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            db.close()
        }
    }
}
