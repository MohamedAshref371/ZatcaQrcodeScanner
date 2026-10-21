package com.poetscorner.zatcaqrcodescanner

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.ScanDatabase
import com.poetscorner.zatcaqrcodescanner.data.ScanEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
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

    @Test fun keepingLatestDayPreservesAllScansFromThatLocalDayEvenOnADstDay() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 3, 8)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        listOf(start - 1, start, end - 1).forEachIndexed { index, time ->
            db.scans().insert(scan(index + 1L, time))
        }
        db.scans().keepLatestDay(zone)
        assertEquals(listOf(3L, 2L), db.scans().all().map { it.id })
    }

    @Test fun keepingLatestDayPreservesOldScansWhenThereAreNoScansToday() = runBlocking {
        val zone = ZoneId.of("Asia/Riyadh")
        val day = LocalDate.of(2020, 1, 15)
        fun time(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        db.scans().insert(scan(1, time(day.minusDays(1), 23)))
        db.scans().insert(scan(2, time(day, 0)))
        db.scans().insert(scan(3, time(day, 23)))
        db.scans().keepLatestDay(zone)
        assertEquals(listOf(3L, 2L), db.scans().all().map { it.id })
    }

    @Test fun keepingLatestDayWithAnEmptyHistoryDoesNothing() = runBlocking {
        db.scans().keepLatestDay()
        assertEquals(emptyList<ScanEntity>(), db.scans().all())
    }

    @Test fun deletingAllAlsoDeletesTodaysScans() = runBlocking {
        db.scans().insert(scan(1, System.currentTimeMillis()))
        db.scans().insert(scan(2, 0))
        db.scans().clear()
        assertEquals(emptyList<ScanEntity>(), db.scans().all())
    }
}
