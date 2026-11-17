package com.poetscorner.invoiceqr

import androidx.room.Room
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.HistoryQueries
import com.poetscorner.invoiceqr.data.InvoicePreferenceStores
import com.poetscorner.invoiceqr.data.REVIEW_USAGE_FILE
import com.poetscorner.invoiceqr.data.ReviewRepository
import com.poetscorner.invoiceqr.data.SCANNER_SETTINGS_FILE
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.SettingsRepository
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.data.ScanEntity
import com.poetscorner.invoiceqr.data.buildInvoiceDatabase
import com.poetscorner.invoiceqr.data.observeCounts
import com.poetscorner.invoiceqr.data.writeTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvoiceStorageIosTest {
    @Test fun writesAndObservesInvoicesAndRollsBackFailedTransactions() = runTest {
        val database = Room.inMemoryDatabaseBuilder<ScanDatabase>().buildInvoiceDatabase()
        try {
            val dao = database.scans()
            val counts = HistoryQueries(HistoryFilter()).counts()
            assertEquals(0, dao.observeCounts(counts).first().matching)
            val observed = async { dao.observeCounts(counts).first { it.matching == 1 } }
            val row = ScanEntity(raw = "AQFB", seller = "A", scannedAt = 42, passed = false, phase = 1)
            val id = database.writeTransaction { dao.saveIfNew(row).scan.id }
            assertEquals(1, observed.await().matching)
            assertEquals(row.copy(id = id), dao.byId(id))
            assertTrue(dao.saveIfNew(row).alreadyExists)

            assertFailsWith<IllegalStateException> {
                database.writeTransaction {
                    dao.insert(row.copy(raw = "AQFC", payloadKey = "different"))
                    error("abort transaction")
                }
            }
            assertEquals(1, dao.count())
        } finally {
            database.close()
        }
    }

    @Test fun createsTheTwoPreferencesFilesAndPersistsSettings() = runTest {
        val files = FileSystem.SYSTEM
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "invoiceqr-${Random.nextLong()}"
        try {
            val stores = InvoicePreferenceStores(directory)
            val settings = SettingsRepository(stores.settings)
            val expected = ScannerSettings(speed = "FAST", cropEnabled = false)
            settings.save(expected)
            assertEquals(expected, settings.settings.first())
            ReviewRepository(stores.review).recordUse(1_700_000_000_000L)
            assertTrue(files.exists(directory / SCANNER_SETTINGS_FILE))
            assertTrue(files.exists(directory / REVIEW_USAGE_FILE))
        } finally {
            files.deleteRecursively(directory, mustExist = false)
        }
    }
}
