package com.poetscorner.zatcaqrcodescanner

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.ScanDatabase
import com.poetscorner.zatcaqrcodescanner.data.invoicePayloadKey
import com.poetscorner.zatcaqrcodescanner.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuousScanningTest {
    @Test fun batchSavesWithoutNavigationCountsOnlyNewInvoicesAndCanOpenTheResult() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val db = Room.databaseBuilder(app, ScanDatabase::class.java, "invoices.db").build()
        val store = ViewModelStore()
        val good = historyFixture(801, seller = "Batch seller", hash = false)
        val review = historyFixture(802, seller = "", hash = false)
        val late = historyFixture(803, seller = "Finishing scan", hash = false)
        try {
            withContext(Dispatchers.Main) {
                val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ScannerViewModel::class.java]
                assertFalse(vm.continuousScan.value.enabled)
                vm.setContinuousScanning(true)
                val feedback = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { vm.scanFeedback.first() } }
                assertTrue(vm.scanned(good.raw))
                feedback.await()
                assertNull(vm.result.value)
                assertNull(vm.message.value)
                assertEquals(1, vm.continuousScan.value.added)
                assertEquals(ContinuousScanStatus.SAVED, vm.continuousScan.value.notice?.status)
                val originalTime = vm.continuousScan.value.notice!!.result.checkedAt
                assertTrue(vm.scanned(" \n" + good.raw.trimEnd('=')))
                assertEquals(1, vm.continuousScan.value.added)
                assertEquals(ContinuousScanStatus.DUPLICATE, vm.continuousScan.value.notice?.status)
                assertEquals(originalTime, vm.continuousScan.value.notice!!.result.checkedAt)
                assertNull(vm.result.value)
                assertTrue(vm.scanned(review.raw))
                assertEquals(2, vm.continuousScan.value.added)
                assertEquals(ContinuousScanStatus.REVIEW, vm.continuousScan.value.notice?.status)
                val lastNotice = vm.continuousScan.value.notice
                assertFalse(vm.scanned("https://example.org"))
                assertEquals(2, vm.continuousScan.value.added)
                assertEquals(lastNotice, vm.continuousScan.value.notice)
                assertEquals(R.string.scan_invalid, vm.message.value?.id)
                vm.message.value = null
                vm.viewContinuousResult()
                assertEquals(review.raw, vm.result.value?.invoice?.raw)
                vm.result.value = null
                vm.setContinuousScanning(false)
                vm.setContinuousScanning(true)
                assertEquals(2, vm.continuousScan.value.added)
                vm.pauseCameraSession()
                assertTrue(vm.continuousScan.value.enabled)
                assertEquals(2, vm.continuousScan.value.added)
                assertNull(vm.continuousScan.value.notice)
                vm.resetCameraSession()
                assertEquals(ContinuousScanState(), vm.continuousScan.value)
                assertTrue(vm.scanned(good.raw))
                assertNotNull(vm.result.value)
                assertEquals(R.string.invoice_already_saved, vm.message.value?.id)
                vm.result.value = null
                vm.message.value = null
                vm.setContinuousScanning(true)
                val inFlight = async(start = CoroutineStart.UNDISPATCHED) { vm.scanned(late.raw) }
                vm.resetCameraSession()
                assertTrue(inFlight.await())
                assertEquals(ContinuousScanState(), vm.continuousScan.value)
                assertNull(vm.result.value)
                // Manual Base64 entry keeps the normal result flow even with batch mode enabled.
                vm.setContinuousScanning(true)
                vm.inspectBase64(good.raw)
                withTimeout(5000) { vm.result.first { it != null } }
                assertEquals(good.raw, vm.result.value?.invoice?.raw)
                assertEquals(0, vm.continuousScan.value.added)
            }
            assertNotNull(db.scans().byPayloadKey(invoicePayloadKey(good.raw)))
            assertNotNull(db.scans().byPayloadKey(invoicePayloadKey(review.raw)))
            assertNotNull(db.scans().byPayloadKey(invoicePayloadKey(late.raw)))
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            listOf(good, review, late).forEach { row ->
                db.scans().byPayloadKey(row.payloadKey)?.let { db.scans().delete(it.id) }
            }
            db.close()
        }
    }
}
