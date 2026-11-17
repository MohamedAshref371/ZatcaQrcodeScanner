package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.*

class StreamingBackupTest {
    @Test fun roundTripsUnicodeEscapesAnnotationsAndCountsDuplicates() = runTest {
        val raw = "AQFB"
        val buffer = Buffer()
        var fetched = false
        HistoryBackupFormat.write(buffer, 1, {
            if (fetched) emptyList() else { fetched = true; listOf(BackupRow(1, raw, 42, "رقم \"١\"", 1, "سطر\nآخر")) }
        })
        var recorded = false
        val summary = HistoryBackupFormat.restore(StreamingBackupJsonInput(buffer)) { invoice, time, annotation ->
            assertEquals(raw, invoice.raw)
            assertEquals(42L, time)
            assertEquals(InvoiceAnnotation("رقم \"١\"", 1, "سطر\nآخر"), annotation)
            recorded = true
            true
        }
        assertTrue(recorded)
        assertEquals(ImportSummary(0, 1), summary)
    }

    @Test fun rejectsMalformedTokensDuplicateKeysAndOversizedRecords() = runTest {
        val empty = """{"format":"invoiceqr-history-backup","version":2,"invoices":[]}"""
        fun row(fields: String) = """{"format":"invoiceqr-history-backup","version":2,"invoices":[{"raw":"AQFB","checkedAt":42,$fields}]}"""
        val invalid = listOf(
            empty + " trailing",
            empty.replace("\"version\":2", "\"version\":2,\"version\":2"),
            empty.replace("\"version\":2", "\"version\":02"),
            empty.replace("\"version\":2", "\"version\":2.0"),
            empty.replace("\"version\":2", "\"version\":\"2\""),
            row("\"note\":\"bad\\q\""), row("\"note\":\"bad\nvalue\""),
            row("\"note\":\"a\",\"note\":\"b\""),
            row("\"note\":\"unterminated"),
            empty.replace("[]", "[,]"), empty.dropLast(1) + ",}",
            empty.replace("\"version\":2", "\"version\":9223372036854775808"),
            row("\"note\":\"" + "a".repeat(700_001) + "\""))
        for (text in invalid) assertFailsWith<InvalidBackupException>(text.take(60)) {
            HistoryBackupFormat.restore(StreamingBackupJsonInput(Buffer().apply { writeString(text) })) { _, _, _ -> false }
        }
    }
}
