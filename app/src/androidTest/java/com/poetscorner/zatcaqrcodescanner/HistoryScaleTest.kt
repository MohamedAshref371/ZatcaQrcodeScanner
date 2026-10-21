package com.poetscorner.zatcaqrcodescanner

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.*
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Writer
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryScaleTest {
    private class CountingWriter(private val delegate: Writer) : Writer() {
        var chars = 0L
        var newlines = 0
        var objects = 0
        override fun write(buffer: CharArray, offset: Int, length: Int) {
            chars += length
            for (index in offset until offset + length) {
                if (buffer[index] == '\n') newlines++
                if (buffer[index] == '{') objects++
            }
            delegate.write(buffer, offset, length)
        }
        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
    private class RecordingDao(private val delegate: ScanDao) : ScanDao by delegate {
        var largestBatch = 0
        var exported = 0
        override suspend fun exportRows(query: SupportSQLiteQuery) = delegate.exportRows(query).also {
            largestBatch = maxOf(largestBatch, it.size)
            exported += it.size
        }
    }

    @Test fun tenThousandInvoicesUseIndexedLookupsSmallPagesAndStreamingExports() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-scale-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, ScanDatabase::class.java, name).build()
        val measurements = linkedMapOf<String, Double>()
        val exportFiles = mutableListOf<File>()
        suspend fun timed(label: String, action: suspend () -> Unit) {
            val start = SystemClock.elapsedRealtimeNanos()
            action()
            measurements[label] = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        }
        try {
            timed("seed_10000_ms") {
                db.withTransaction {
                    for (start in 1..10000 step 100) {
                        db.scans().insertBatch((start until start + 100).map { historyFixture(it) })
                    }
                }
            }
            timed("first_page_50_ms") {
                val page = db.scans().summaries(HistoryQueries(HistoryFilter()).page())
                assertEquals(50, page.size)
                assertEquals(10000, db.scans().observeCounts(HistoryQueries(HistoryFilter()).counts()).first().matching)
            }
            timed("search_and_date_ms") {
                val filter = HistoryFilter("متجر 42", historyBaseDay + 12, historyBaseDay + 12)
                val queries = HistoryQueries(filter)
                val expected = (1..10000).count { it % 100 == 42 && it % 30 == 12 }
                assertEquals(expected, db.scans().observeCounts(queries.counts()).first().matching)
                assertEquals(expected, db.scans().summaries(queries.page()).size)
            }
            val original = requireNotNull(db.scans().byPayloadKey(historyFixture(1).payloadKey))
            timed("duplicate_lookup_100_ms") {
                repeat(100) {
                    val repeated = db.scans().saveIfNew(original.copy(id = 0, scannedAt = Long.MAX_VALUE))
                    assertTrue(repeated.alreadyExists)
                    assertEquals(original.id, repeated.scan.id)
                    assertEquals(original.scannedAt, repeated.scan.scannedAt)
                }
            }
            timed("open_result_ms") {
                val selected = requireNotNull(db.scans().byId(original.id))
                assertNull(ZatcaParser.parse(selected.raw).error)
            }
            val names = mapOf(1 to "Seller", 2 to "VAT", 3 to "Date", 4 to "Total", 5 to "Tax", 6 to "Hash")
            for (csv in listOf(true, false)) {
                val recorder = RecordingDao(db.scans())
                val file = File(context.cacheDir, "$name.${if (csv) "csv" else "json"}")
                exportFiles += file
                val writer = CountingWriter(file.bufferedWriter(Charsets.UTF_8))
                timed(if (csv) "csv_10000_ms" else "json_10000_ms") {
                    writer.use { db.withTransaction { HistoryExporter.write(recorder, HistoryFilter(), it, csv, names) } }
                }
                assertEquals(10000, recorder.exported)
                assertEquals(200, recorder.largestBatch)
                assertTrue(writer.chars > 500000)
                if (csv) assertEquals(10000, writer.newlines) else assertEquals(10000, writer.objects)
            }
            val metricLine = measurements.entries.joinToString(" ") { (key, value) -> "$key=${"%.2f".format(java.util.Locale.US, value)}" }
            Log.i("HistoryScaleBenchmark", metricLine + " page_size=50 export_batch=200 database_bytes=" + context.getDatabasePath(name).length())
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("history_scale_metrics", metricLine)
            })
        } finally {
            db.close()
            context.deleteDatabase(name)
            exportFiles.forEach { it.delete() }
        }
    }
}
