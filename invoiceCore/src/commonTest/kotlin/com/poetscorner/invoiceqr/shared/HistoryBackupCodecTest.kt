package com.poetscorner.invoiceqr.shared

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HistoryBackupCodecTest {
    private val raw = Base64.encode(byteArrayOf(1, 1, 65))

    @Test
    fun versionTwoRoundTripAcrossChunkBoundaries() {
        val row = BackupInvoice("  $raw\n", 123, "رقم ٣", 99, "Quote \" slash \\ \n emoji 🧾 \u0000")
        val document = HistoryBackupCodec.header() + HistoryBackupCodec.record(row, first = true) + HistoryBackupCodec.footer()
        val reader = read(document)
        val result = reader.next()
        assertEquals(row, result?.record)
        assertEquals("A", result?.invoice?.value(1))
        assertNull(reader.next())
    }

    @Test
    fun versionOneAllowsHeaderAfterRecordsAndDefaultsAnnotations() {
        val reader = read("""{"invoices":[{"checkedAt":42,"raw":"$raw"}],"version":1,"format":"invoiceqr-history-backup"}""")
        assertEquals(BackupInvoice(raw, 42), reader.next()?.record)
        assertNull(reader.next())
    }

    @Test
    fun aValidFirstRecordDoesNotMakeAnInvalidDocumentValid() {
        val entry = """{"checkedAt":42,"raw":"$raw"}"""
        val prefix = """{"format":"invoiceqr-history-backup","version":1,"invoices":["""
        for (suffix in listOf("", ",]", "]}}", """,{"raw":"$raw","checkedAt":43,"category":3}]}""")) {
            val reader = read(prefix + entry + suffix)
            assertEquals(raw, reader.next()?.record?.raw)
            assertFailsWith<InvalidHistoryBackup> { while (reader.next() != null) Unit }
        }
    }

    @Test
    fun rejectsDuplicateFieldsCoercionsAndOversizedStrings() {
        val records = listOf(
            """{"raw":"$raw","raw":"$raw","checkedAt":42}""",
            """{"raw":"$raw","checkedAt":"42"}""",
            """{"raw":"$raw","checkedAt":42.0}""",
            """{"raw":"$raw","checkedAt":42,"category":7}""",
            """{"raw":"$raw","checkedAt":42,"note":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}""",
        )
        for (record in records) {
            val reader = read(HistoryBackupCodec.header() + record + HistoryBackupCodec.footer())
            assertFailsWith<InvalidHistoryBackup> { reader.next() }
        }
    }

    private fun read(document: String): HistoryBackupReader {
        val chunks = document.chunked(7).iterator()
        return HistoryBackupReader { if (chunks.hasNext()) chunks.next() else null }
    }
}
