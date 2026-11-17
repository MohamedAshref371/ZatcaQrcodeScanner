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
        val invalid = listOf(
            "{} trailing", "{\"format\":\"a\",\"format\":\"b\"}",
            "{\"version\":01}", "{\"version\":1.0}", "{\"version\":\"1\"}",
            "{\"format\":\"bad\\q\"}", "{\"format\":\"bad\nvalue\"}", "{\"format\":\"unterminated",
            "{\"invoices\":[,]}", "{\"invoices\":[],}", "{\"version\":9223372036854775808}",
            "{\"format\":\"" + "a".repeat(700_001) + "\"}")
        for (text in invalid) assertFailsWith<InvalidBackupException>(text.take(60)) {
            HistoryBackupFormat.restore(StreamingBackupJsonInput(Buffer().apply { writeString(text) })) { _, _, _ -> false }
        }
    }
}
