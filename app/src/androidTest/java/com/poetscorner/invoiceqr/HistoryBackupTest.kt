package com.poetscorner.invoiceqr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter

@RunWith(AndroidJUnit4::class)
class HistoryBackupTest {
    private lateinit var source: ScanDatabase
    private lateinit var target: ScanDatabase
    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        source = inMemoryScanDatabase(context)
        target = inMemoryScanDatabase(context)
    }
    @After fun tearDown() { source.close(); target.close() }

    private fun document(rows: List<ScanEntity>, version: Int = 1): String = JSONObject()
        .put("format", "invoiceqr-history-backup").put("version", version)
        .put("invoices", JSONArray().apply {
            rows.forEach { put(JSONObject().put("raw", it.raw).put("checkedAt", it.scannedAt)) }
        }).toString()

    @Test fun roundTripAcrossBatchesPreservesOriginalQrDatesAndExistingDuplicates() = runBlocking {
        val rows = (1..235).map { historyFixture(94000 + it, seller = "Backup seller $it")
            .copy(invoiceNumber = "INV-$it", category = InvoiceCategories.ids[it % InvoiceCategories.ids.size], note = "Note $it") }
        source.scans().insertBatch(rows)
        val existing = rows.first().copy(raw = " \n" + rows.first().raw.trimEnd('='),
            scannedAt = 1234L, invoiceNumber = "LOCAL-1", category = 99, note = "Keep local note")
        target.scans().saveIfNew(existing)
        val progress = mutableListOf<Pair<Int, Int>>()
        val output = StringWriter()
        HistoryBackup.write(source, output) { done, total -> progress += done to total }
        assertEquals(235 to 235, progress.last())
        assertTrue(progress.contains(200 to 235))
        val summary = HistoryBackup.restore(target, StringReader(output.toString()))
        assertEquals(ImportSummary(234, 1), summary)
        val restored = target.scans().all().associateBy { it.payloadKey }
        assertEquals(235, restored.size)
        for (row in rows.drop(1)) {
            val actual = requireNotNull(restored[row.payloadKey])
            assertEquals(row.raw, actual.raw)
            assertEquals(row.scannedAt, actual.scannedAt)
            assertEquals(row.details, actual.details)
            assertEquals(row.invoiceNumber, actual.invoiceNumber)
            assertEquals(row.category, actual.category)
            assertEquals(row.note, actual.note)
        }
        assertEquals(existing.raw, restored[existing.payloadKey]?.raw)
        assertEquals(1234L, restored[existing.payloadKey]?.scannedAt)
        assertEquals("LOCAL-1", restored[existing.payloadKey]?.invoiceNumber)
        assertEquals(99, restored[existing.payloadKey]?.category)
        assertEquals("Keep local note", restored[existing.payloadKey]?.note)
        assertEquals(ImportSummary(0, 235), HistoryBackup.restore(target, StringReader(output.toString())))
        assertEquals(235, target.scans().count())
    }

    @Test fun malformedFinalRecordRollsBackAllEarlierRowsAndKeepsExistingHistory() = runBlocking {
        val existing = historyFixture(95000, hash = false)
        target.scans().saveIfNew(existing)
        val rows = (1..110).map { historyFixture(95000 + it, hash = false) }
        val json = JSONObject(document(rows))
        json.getJSONArray("invoices").put(JSONObject().put("raw", "invalid").put("checkedAt", 123))
        try {
            HistoryBackup.restore(target, StringReader(json.toString()))
            fail("Invalid records must abort the entire import")
        } catch (_: InvalidBackupException) { }
        assertEquals(listOf(existing.raw), target.scans().all().map { it.raw })
    }

    @Test fun unsupportedTruncatedAndOrdinaryExportFilesAreRejectedWithoutChanges() = runBlocking {
        val row = historyFixture(96001, hash = false)
        val valid = document(listOf(row))
        val cases = listOf(document(listOf(row), version = 3), valid.dropLast(3), "[]", valid + " {}",
            valid.replace("\"checkedAt\":${row.scannedAt}", "\"checkedAt\":-1"),
            valid.replace("\"checkedAt\":${row.scannedAt}", "\"checkedAt\":1.5"),
            valid.replace("\"version\":1", "\"version\":1,\"version\":1"))
        for (json in cases) {
            try {
                HistoryBackup.restore(target, StringReader(json))
                fail("Unexpectedly accepted: $json")
            } catch (_: InvalidBackupException) { }
            assertEquals(0, target.scans().count())
        }
    }

    @Test fun oldBackupsRestoreEmptyAnnotationsAndInvalidCategoryRollsBack() = runBlocking {
        val row = historyFixture(96501, hash = false)
        assertEquals(ImportSummary(1, 0), HistoryBackup.restore(target, StringReader(document(listOf(row)))))
        val restored = requireNotNull(target.scans().byPayloadKey(row.payloadKey))
        assertEquals("", restored.invoiceNumber)
        assertEquals(0, restored.category)
        assertEquals("", restored.note)
        val bad = JSONObject(document(listOf(historyFixture(96502)), version = 2))
        bad.getJSONArray("invoices").getJSONObject(0).put("category", 7)
        try {
            HistoryBackup.restore(target, StringReader(bad.toString()))
            fail("Unknown category must reject the backup")
        } catch (_: InvalidBackupException) { }
        assertEquals(1, target.scans().count())
    }

    @Test fun cancellationDuringImportRollsBackAndIsNotReportedAsInvalidBackup() = runBlocking {
        val rows = (1..110).map { historyFixture(97000 + it, hash = false) }
        try {
            HistoryBackup.restore(target, StringReader(document(rows))) { throw CancellationException("test") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(0, target.scans().count())
    }

    @Test fun repeatedRowsInsideABackupAreMergedAndEmptyBackupsAreValid() = runBlocking {
        val output = StringWriter()
        HistoryBackup.write(source, output)
        assertEquals(ImportSummary(0, 0), HistoryBackup.restore(target, StringReader(output.toString())))
        val row = historyFixture(98001, hash = false)
        assertEquals(ImportSummary(1, 1), HistoryBackup.restore(target, StringReader(document(listOf(row, row)))))
    }

    @Test fun oversizedJsonStringAndOversizedInputAreBounded() = runBlocking {
        try {
            HistoryBackup.restore(target, StringReader("{\"format\":\"" + "x".repeat(710000) + "\"}"))
            fail("Oversized single value must be rejected")
        } catch (_: InvalidBackupException) { }
        val input = object : InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = length
        }
        val sink = object : OutputStream() {
            override fun write(value: Int) { }
            override fun write(buffer: ByteArray, offset: Int, length: Int) { }
        }
        try {
            HistoryBackup.copyLimited(input, sink)
            fail("Oversized file must be rejected")
        } catch (_: BackupTooLargeException) { }
        assertEquals(0, target.scans().count())
    }
}
