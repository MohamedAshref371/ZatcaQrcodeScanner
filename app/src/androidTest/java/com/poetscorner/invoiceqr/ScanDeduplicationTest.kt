package com.poetscorner.invoiceqr

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.data.ScanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanDeduplicationTest {
    private lateinit var db: ScanDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
    }
    @After fun tearDown() { db.close() }
    private fun scan(raw: String = "AQJBQg==", time: Long = 100) = ScanEntity(
        raw = raw, seller = "AB", scannedAt = time, passed = false, phase = 1)

    @Test fun repeatedPayloadReturnsTheOriginalRowAndTime() = runBlocking {
        val first = db.scans().saveIfNew(scan())
        val repeated = db.scans().saveIfNew(scan(time = 900))
        assertFalse(first.alreadyExists)
        assertTrue(repeated.alreadyExists)
        assertEquals(first.scan, repeated.scan)
        assertEquals(listOf(first.scan), db.scans().all())
    }

    @Test fun copiedWhitespaceAndMissingPaddingDoNotCreateDuplicates() = runBlocking {
        val first = db.scans().saveIfNew(scan(raw = " AQJB\nQg==\r\n"))
        val repeated = db.scans().saveIfNew(scan(raw = "AQJBQg", time = 900))
        assertTrue(repeated.alreadyExists)
        assertEquals(first.scan, repeated.scan)
        assertEquals(" AQJB\nQg==\r\n", repeated.scan.raw)
        assertEquals(1, db.scans().all().size)
    }

    @Test fun concurrentChecksSaveOnlyOneCopy() = runBlocking {
        val results = coroutineScope {
            (1..12).map { index -> async(Dispatchers.Default) {
                db.scans().saveIfNew(scan(time = index.toLong()))
            } }.awaitAll()
        }
        assertEquals(1, results.count { !it.alreadyExists })
        assertEquals(1, results.map { it.scan }.distinct().size)
        assertEquals(1, db.scans().all().size)
    }

    @Test fun differentPayloadsAreSavedAndDeletedInvoicesCanBeAddedAgain() = runBlocking {
        val first = db.scans().saveIfNew(scan())
        assertFalse(db.scans().saveIfNew(scan(raw = "AQJDRA==")).alreadyExists)
        assertEquals(2, db.scans().all().size)
        db.scans().delete(first.scan.id)
        val addedAgain = db.scans().saveIfNew(scan(time = 800))
        assertFalse(addedAgain.alreadyExists)
        assertEquals(800L, addedAgain.scan.scannedAt)
        assertEquals(2, db.scans().all().size)
    }
}
