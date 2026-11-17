package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.resources.*

import com.poetscorner.invoiceqr.data.openScanDatabase
import android.app.Application
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultRestorationTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val viewModelJobs = mutableListOf<Job>()

    private fun create(store: ViewModelStore, state: SavedStateHandle): ScannerViewModel =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelClass.cast(ScannerViewModel(app, state).also {
                    viewModelJobs += it.viewModelScope.coroutineContext[Job]!!
                })!!
        })[ScannerViewModel::class.java]

    // Parcel the saved state and use a new ViewModelStore, so no retained ViewModel or Invoice
    // can accidentally make this pass. A real process-death check is also documented separately.
    private fun roundTrip(state: SavedStateHandle): SavedStateHandle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state.savedStateProvider().saveState())
            assertTrue("Only a row ID and screen should be saved", parcel.dataSize() < 4096)
            parcel.setDataPosition(0)
            return SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }

    @Test fun restoresInvoiceAndOriginalCheckTimeWithoutInsertingOrChangingTheCurrentTab() = runBlocking {
        val db = openScanDatabase(app)
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        val fixture = historyFixture(92001, seller = "Restored invoice", hash = false)
        try {
            withContext(Dispatchers.Main) {
                val state = SavedStateHandle()
                val first = create(firstStore, state)
                first.scanned(fixture.raw)
                val original = requireNotNull(first.result.value)
                first.navigate(2)
                val restoredState = roundTrip(state)
                assertEquals(original.scanId, restoredState.get<Long>("result_scan_id"))
                val firstJob = first.viewModelScope.coroutineContext[Job]!!
                firstStore.clear()
                firstJob.join()
                val second = create(secondStore, restoredState)
                withTimeout(10000) { second.restoringResult.first { !it } }
                assertNull("Restoration error", second.message.value)
                val restored = requireNotNull(second.result.value) { "Missing restored result; saved ID=${restoredState.get<Long>("result_scan_id")}" }
                assertEquals(original.invoice.raw, restored.invoice.raw)
                assertEquals(original.checkedAt, restored.checkedAt)
                assertEquals(original.scanId, restored.scanId)
                assertEquals(2, second.screen.value)
                secondStore.clear()
                viewModelJobs.forEach { it.join() }
                assertEquals(original.scanId, db.scans().byPayloadKey(fixture.payloadKey)?.id)
                assertEquals(original.checkedAt, db.scans().byPayloadKey(fixture.payloadKey)?.scannedAt)
            }
        } finally {
            withContext(Dispatchers.Main) {
                firstStore.clear(); secondStore.clear()
                viewModelJobs.forEach { it.join() }
            }
            db.scans().byPayloadKey(fixture.payloadKey)?.let { db.scans().delete(it.id) }
            db.close()
        }
    }

    @Test fun restoresTheResultTabAndClearingItPreventsItFromReturning() = runBlocking {
        val db = openScanDatabase(app)
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        val fixture = historyFixture(92002, seller = "Dismissed invoice", hash = false)
        try {
            withContext(Dispatchers.Main) {
                val state = SavedStateHandle()
                val first = create(firstStore, state)
                first.scanned(fixture.raw)
                val saved = roundTrip(state)
                assertEquals(first.result.value?.scanId, saved.get<Long>("result_scan_id"))
                val firstJob = first.viewModelScope.coroutineContext[Job]!!
                firstStore.clear()
                firstJob.join()
                val second = create(secondStore, saved)
                withTimeout(10000) { second.restoringResult.first { !it } }
                assertNull("Restoration error", second.message.value)
                assertNotNull(second.result.value)
                assertEquals(1, second.screen.value)
                second.navigate(0)
                assertNull(second.result.value)
                assertFalse(saved.contains("result_scan_id"))
                assertEquals(0, roundTrip(saved).get<Int>("screen"))
            }
        } finally {
            withContext(Dispatchers.Main) {
                firstStore.clear(); secondStore.clear()
                viewModelJobs.forEach { it.join() }
            }
            db.scans().byPayloadKey(fixture.payloadKey)?.let { db.scans().delete(it.id) }
            db.close()
        }
    }

    @Test fun backReturnsToTheInvoiceSourceAfterRestorationAndNewScansReturnToCamera() = runBlocking {
        val db = openScanDatabase(app)
        val fixture = historyFixture(92004, seller = "Return destination", hash = false)
        try {
            val id = db.scans().saveIfNew(fixture).scan.id
            for (source in listOf(2, 4)) {
                val firstStore = ViewModelStore()
                val secondStore = ViewModelStore()
                try {
                    withContext(Dispatchers.Main) {
                        val state = SavedStateHandle(mapOf("screen" to source))
                        val first = create(firstStore, state)
                        if (source == 2) first.open(id) else first.openStatisticsInvoice(id)
                        withTimeout(10000) { first.restoringResult.first { !it } }
                        assertEquals(1, first.screen.value)
                        val restored = roundTrip(state)
                        val firstJob = first.viewModelScope.coroutineContext[Job]!!
                        firstStore.clear()
                        firstJob.join()
                        val second = create(secondStore, restored)
                        withTimeout(10000) { second.restoringResult.first { !it } }
                        second.backFromResultOrHome()
                        assertEquals(source, second.screen.value)
                        second.navigate(0)
                        second.scanned(fixture.raw)
                        assertEquals(1, second.screen.value)
                        second.backFromResultOrHome()
                        assertEquals(0, second.screen.value)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        firstStore.clear(); secondStore.clear()
                        viewModelJobs.forEach { it.join() }
                    }
                }
            }
        } finally {
            db.scans().byPayloadKey(fixture.payloadKey)?.let { db.scans().delete(it.id) }
            db.close()
        }
    }

    @Test fun tabBackRemembersOnlyOnePageAndCanStartAgainAfterANewSelection() = runBlocking {
        val store = ViewModelStore()
        try {
            withContext(Dispatchers.Main) {
                val vm = create(store, SavedStateHandle())
                vm.selectTab(4)
                vm.selectTab(2)
                vm.selectTab(2)
                vm.backFromResultOrHome()
                assertEquals(4, vm.screen.value)
                assertNull(vm.previousTab.value)
                vm.backFromResultOrHome()
                assertEquals(0, vm.screen.value)

                vm.selectTab(2)
                vm.selectTab(4)
                vm.selectTab(3)
                vm.backFromResultOrHome()
                assertEquals(4, vm.screen.value)
                vm.selectTab(2)
                vm.backFromResultOrHome()
                assertEquals(4, vm.screen.value)
                vm.backFromResultOrHome()
                assertEquals(0, vm.screen.value)

                vm.selectTab(4)
                vm.selectTab(0)
                vm.backFromResultOrHome()
                assertEquals(4, vm.screen.value)
                vm.backFromResultOrHome()
                assertEquals(0, vm.screen.value)
            }
        } finally {
            withContext(Dispatchers.Main) {
                store.clear()
                viewModelJobs.forEach { it.join() }
            }
        }
    }

    @Test fun aDeletedInvoiceIsNotRecreatedDuringRestoration() = runBlocking {
        val db = openScanDatabase(app)
        val store = ViewModelStore()
        val fixture = historyFixture(92003, seller = "Deleted invoice", hash = false)
        try {
            val id = db.scans().saveIfNew(fixture).scan.id
            db.scans().delete(id)
            db.close()
            withContext(Dispatchers.Main) {
                val state = SavedStateHandle(mapOf("result_scan_id" to id, "screen" to 1))
                val restoredState = roundTrip(state)
                val vm = create(store, restoredState)
                withTimeout(5000) { vm.restoringResult.first { !it } }
                assertNull(vm.result.value)
                assertEquals(Res.string.history_entry_missing, vm.message.value?.id)
                assertFalse(restoredState.contains("result_scan_id"))
            }
        } finally {
            withContext(Dispatchers.Main) {
                store.clear()
                viewModelJobs.forEach { it.join() }
            }
            db.close()
        }
    }
}
