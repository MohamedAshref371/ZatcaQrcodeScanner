package com.poetscorner.zatcaqrcodescanner

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringWriter
import java.time.Instant
import java.util.Base64
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser

@RunWith(AndroidJUnit4::class)
class HistoryStorageTest {
    private lateinit var db: ScanDatabase
    private val names = mapOf(1 to "Seller", 2 to "VAT", 3 to "Date", 4 to "Total", 5 to "Tax", 6 to "Hash")
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
    }
    @After fun tearDown() { db.close() }

    @Test fun databaseFiltersAndKeysetPagesMatchTheExistingSearchSemantics() = runBlocking {
        val fixtures = (1..12).map { i -> historyFixture(i, seller = when (i) {
            1 -> "North Market"; 2 -> "literal %_"; 3 -> "متجر ١٢٣"; else -> "متجر $i"
        }, date = if (i == 12) "invalid" else "2026-10-${(i + 1).toString().padStart(2, '0')}T00:30:00+03:00",
            time = Instant.parse("2026-11-01T04:00:00Z").toEpochMilli() + i * 3600000L) }
        db.scans().insertBatch(fixtures)
        val stored = db.scans().all()
        val filters = listOf(HistoryFilter(), HistoryFilter("NORTH"), HistoryFilter("%_"), HistoryFilter("١٢٣"),
            HistoryFilter("٣٠٠٠٠٠٠٠٠٠٠٠٠٠١"), HistoryFilter("' OR 1=1 --"), HistoryFilter("no match"),
            HistoryFilter(fromDay = historyBaseDay + 2, toDay = historyBaseDay + 8),
            HistoryFilter(fromDay = historyBaseDay + 2), HistoryFilter(toDay = historyBaseDay + 8),
            HistoryFilter(fromDay = 20758, toDay = 20758, dateSource = HistoryDateSource.SCAN, zoneId = "America/New_York"))
        for (filter in filters) {
            val expected = filter.apply(stored.map { HistoryEntry.from(it) }).map { it.scan.id }
            val queries = HistoryQueries(filter)
            val counts = db.scans().observeCounts(queries.counts()).first()
            assertEquals(12, counts.total)
            assertEquals(filter.toString(), expected.size, counts.matching)
            var cursor: HistoryCursor? = null
            val actual = mutableListOf<Long>()
            while (true) {
                val page = db.scans().summaries(queries.page(cursor, limit = 3))
                assertTrue(page.size <= 3)
                if (page.isEmpty()) break
                actual += page.map { it.id }
                cursor = page.last().let { HistoryCursor(it.scannedAt, it.id) }
            }
            assertEquals(filter.toString(), expected, actual)
            assertEquals(actual.size, actual.distinct().size)
        }
        val loaded = requireNotNull(db.scans().byId(stored.first().id))
        assertEquals(stored.first().raw, loaded.raw)
        assertEquals("115.00", loaded.details.total)
    }

    @Test fun streamingExportsMatchExistingFormatsAcrossPagesAndFilters() = runBlocking {
        db.scans().insertBatch((1..260).map { historyFixture(it, seller = if (it == 1) "=SUM(1,2)\"" else "Seller $it", hash = it == 1) })
        val blankHash = historyFixture(261, seller = "Whitespace hash", hash = false)
        val blankRaw = Base64.getEncoder().encodeToString(Base64.getDecoder().decode(blankHash.raw) + byteArrayOf(6, 3, 32, 9, 10))
        db.scans().insert(ScanEntity.from(ZatcaParser.parse(blankRaw), blankHash.scannedAt))
        val stored = db.scans().all()
        for (filter in listOf(HistoryFilter(), HistoryFilter("Seller 25"), HistoryFilter("missing"), HistoryFilter("Whitespace hash"),
            HistoryFilter(fromDay = historyBaseDay + 3, toDay = historyBaseDay + 12))) {
            val expected = filter.apply(stored.map { HistoryEntry.from(it) }).map { it.scan }
            for (csv in listOf(true, false)) {
                val writer = StringWriter()
                db.withTransaction { HistoryExporter.write(db.scans(), filter, writer, csv, names) }
                val actual = writer.toString()
                if (csv) assertEquals(HistoryExporter.csv(expected, names), actual)
                else assertEquals(JSONArray(HistoryExporter.json(expected, names)).toString(), JSONArray(actual).toString())
            }
        }
    }

    @Test fun theUniqueIndexAlsoProtectsDirectInserts() = runBlocking {
        val first = historyFixture(1)
        assertTrue(db.scans().insert(first) > 0)
        assertEquals(-1L, db.scans().insert(first.copy(raw = first.raw.chunked(10).joinToString("\n"), scannedAt = first.scannedAt + 100)))
        val plan = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM scans WHERE payloadKey = ?", arrayOf(first.payloadKey)).use { cursor ->
            buildString { while (cursor.moveToNext()) append(cursor.getString(3)) }
        }
        assertTrue(plan, plan.contains("index_scans_payloadKey"))
        assertEquals(1, db.scans().all().size)
    }
}
