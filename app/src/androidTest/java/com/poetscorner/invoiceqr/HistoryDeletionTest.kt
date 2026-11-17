package com.poetscorner.invoiceqr

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.data.ScanEntity
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryDeletionPeriod
import com.poetscorner.invoiceqr.data.HistoryDeletionPlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate
import kotlinx.datetime.TimeZone
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class HistoryDeletionTest {
    private lateinit var db: ScanDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ScanDatabase::class.java
        ).build()
    }

    @After fun tearDown() { db.close() }

    private fun scan(id: Long, time: Long) = ScanEntity(
        id = id, raw = "test QR $id", seller = "Seller", scannedAt = time, passed = true, phase = 1
    )

    @Test fun deletingOneScanPreservesOtherInvoices() = runBlocking {
        db.scans().insert(scan(1, 100))
        db.scans().insert(scan(2, 100))
        db.scans().delete(1)
        assertEquals(listOf(2L), db.scans().all().map { it.id })
    }

    @Test fun scanDateDeletionUsesLocalDayBoundaryEvenOnADstDay() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 3, 11)
        val start = today.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        listOf(start - 1, start, start + 1).forEachIndexed { index, time ->
            db.scans().insert(scan(index + 1L, time))
        }
        val plan = HistoryDeletionPlan.create(HistoryDateSource.SCAN,
            HistoryDeletionPeriod.THREE_DAYS, today.toKotlinLocalDate(), TimeZone.of(zone.id))
        assertEquals(1, db.scans().deletionCount(plan))
        db.scans().deleteSelection(plan)
        assertEquals(listOf(3L, 2L), db.scans().all().map { it.id })
    }

    @Test fun invoiceDateDeletionKeepsMissingDatesAndIgnoresScanDate() = runBlocking {
        val today = LocalDate.of(2026, 11, 5)
        val cutoff = today.minusYears(2).toEpochDay()
        db.scans().insert(scan(1, 0).copy(details = scan(1, 0).details.copy(invoiceDay = cutoff - 1)))
        db.scans().insert(scan(2, 0).copy(details = scan(2, 0).details.copy(invoiceDay = cutoff)))
        db.scans().insert(scan(3, 0))
        val plan = HistoryDeletionPlan.create(HistoryDateSource.INVOICE,
            HistoryDeletionPeriod.TWO_YEARS, today.toKotlinLocalDate())
        assertEquals(1, db.scans().deletionCount(plan))
        db.scans().deleteSelection(plan)
        assertEquals(listOf(3L, 2L), db.scans().all().map { it.id })
    }

    @Test fun deletingAllAlsoDeletesTodaysScans() = runBlocking {
        db.scans().insert(scan(1, System.currentTimeMillis()))
        db.scans().insert(scan(2, 0))
        val plan = HistoryDeletionPlan.create(HistoryDateSource.INVOICE, HistoryDeletionPeriod.ALL)
        assertEquals(2, db.scans().deletionCount(plan))
        db.scans().deleteSelection(plan)
        assertEquals(emptyList<ScanEntity>(), db.scans().all())
    }
}
