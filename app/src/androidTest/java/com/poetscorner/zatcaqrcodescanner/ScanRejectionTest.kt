package com.poetscorner.zatcaqrcodescanner

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.MIGRATION_1_2
import com.poetscorner.zatcaqrcodescanner.data.ScanDatabase
import com.poetscorner.zatcaqrcodescanner.ui.ScannerViewModel
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
        val db = Room.databaseBuilder(app, ScanDatabase::class.java, "invoices.db")
            .addMigrations(MIGRATION_1_2).build()
        val store = ViewModelStore()
        try {
            val before = db.scans().all()
            withContext(Dispatchers.Main) {
                val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ScannerViewModel::class.java]
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
