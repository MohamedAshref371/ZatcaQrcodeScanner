package com.poetscorner.invoiceqr

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class StatisticsStorageTest {
    private lateinit var db: ScanDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
    }
    @After fun close() { db.close() }

    private suspend fun calculate(request: StatisticsRequest) = db.statistics(request)

    @Test fun readsEveryPageAndIncludesFailedInvoicesAndIndependentValidAmounts() = runBlocking {
        db.scans().insertBatch((1..450).map { historyFixture(it, seller = "Market", hash = false) })
        val invalid = historyFixture(451, seller = "Market", date = "invalid", hash = false)
        db.scans().insert(invalid.copy(details = invalid.details.copy(total = "invalid", tax = "1.00")))
        val result = calculate(StatisticsRequest())
        assertEquals(451, result.summary.count)
        assertEquals(BigDecimal("51750.00"), result.summary.total)
        assertEquals(BigDecimal("6751.00"), result.summary.tax)
        assertEquals(1, result.summary.missingTotal)
        val dates = calculate(StatisticsRequest(grouping = StatisticsGrouping.DAY))
        assertEquals(1, dates.groups.last().totals.count)
        assertTrue(dates.groups.last().key.unknown)
    }

    @Test fun filtersAreAndedLiteralAndInclusiveAndUnknownDatesCannotMatchADateRange() = runBlocking {
        db.scans().insertBatch(listOf(
            historyFixture(1, seller = "North %_", date = "2026-10-01T23:59:59Z"),
            historyFixture(2, seller = "North %_", date = "2026-10-02T00:00:00+03:00"),
            historyFixture(3, seller = "South", date = "2026-10-02T12:00:00Z"),
            historyFixture(4, seller = "North %_", date = "invalid")))
        assertEquals(2, calculate(StatisticsRequest(fromDay = historyBaseDay, toDay = historyBaseDay + 1, seller = "NORTH")).summary.count)
        assertEquals(1, calculate(StatisticsRequest(fromDay = historyBaseDay + 1, toDay = historyBaseDay + 1,
            seller = "%_", vat = "0002")).summary.count)
        assertEquals(0, calculate(StatisticsRequest(seller = "South", vat = "0002")).summary.count)
        assertEquals(1, calculate(StatisticsRequest(vat = "٠٠٠٢")).summary.count)
        assertEquals(0, calculate(StatisticsRequest(seller = "' OR 1=1 --")).summary.count)
        assertEquals(3, calculate(StatisticsRequest(seller = "%_")).summary.count)
        assertEquals(3, calculate(StatisticsRequest(toDay = historyBaseDay + 1)).summary.count)
    }
}
