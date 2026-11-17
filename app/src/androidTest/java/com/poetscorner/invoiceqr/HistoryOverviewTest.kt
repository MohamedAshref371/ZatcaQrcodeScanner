package com.poetscorner.invoiceqr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryOverviewTest {
    @Test fun exactSumIncludesAllPagesAndHonorsFiltersAndDeletion() = runBlocking<Unit> {
        val db = inMemoryScanDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val dao = db.scans()
            val rows = (1..405).map { index ->
                val row = historyFixture(index, seller = if (index <= 2) "Decimals" else "Other")
                row.copy(details = row.details.copy(total = when (index) {
                    1 -> "0.10"
                    2 -> "0.20"
                    405 -> "10000000000000000000.99"
                    else -> "1.00"
                }))
            }
            dao.insertBatch(rows)
            val all = dao.overview(HistoryFilter())
            assertEquals(405, all.counts.matching)
            assertEquals(50, all.rows.size)
            assertEquals("10000000000000000403.29", all.amount?.toPlainString())
            val decimals = dao.overview(HistoryFilter("Decimals"))
            assertEquals(2, decimals.counts.matching)
            assertEquals("0.30", decimals.amount?.toPlainString())
            val dated = dao.overview(HistoryFilter("Decimals", historyBaseDay + 1, historyBaseDay + 1))
            assertEquals(1, dated.counts.matching)
            assertEquals("0.10", dated.amount?.toPlainString())
            dao.delete(decimals.rows.first().id)
            assertEquals("0.10", dao.overview(HistoryFilter("Decimals")).amount?.toPlainString())
            val empty = dao.overview(HistoryFilter("absent"))
            assertEquals(0, empty.counts.matching)
            assertEquals("0.00", empty.amount?.toPlainString())
            val invalid = historyFixture(406, seller = "Invalid")
            dao.insert(invalid.copy(details = invalid.details.copy(total = "bad amount")))
            assertNull(dao.overview(HistoryFilter()).amount)
            assertEquals("0.10", dao.overview(HistoryFilter("Decimals")).amount?.toPlainString())
        } finally { db.close() }
    }
}
