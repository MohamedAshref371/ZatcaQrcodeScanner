package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonPrimitive

data class BackupRow(val id: Long, val raw: String, val scannedAt: Long,
    val invoiceNumber: String, val category: Int, val note: String)
data class ImportSummary(val added: Int, val duplicates: Int)
class InvalidBackupException(cause: Throwable? = null) : IOException("Invalid invoice history backup", cause)
class BackupTooLargeException : IOException("Backup exceeds 64 MiB")

/** A streaming JSON reader supplied by the platform file adapter. */
interface BackupJsonInput {
    fun beginObject()
    fun endObject()
    fun beginArray()
    fun endArray()
    fun hasNext(): Boolean
    fun nextName(): String
    fun nextString(): String
    fun nextInteger(): Long
    fun resetRecord()
    fun endOfDocument(): Boolean
}

/** Language-independent, versioned history format. Derived invoice fields are rebuilt on import. */
object HistoryBackupFormat {
    private const val FORMAT = "invoiceqr-history-backup"
    private const val VERSION = 2
    const val MAX_BYTES = 64L * 1024 * 1024

    suspend fun copyLimited(input: RawSource, output: RawSink, maxBytes: Long = MAX_BYTES) {
        val buffer = Buffer()
        var bytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.readAtMostTo(buffer, 8192)
            if (count == -1L) return
            if (count > maxBytes - bytes) throw BackupTooLargeException()
            bytes += count
            output.write(buffer, count)
        }
    }

    suspend fun write(sink: Sink, total: Int, page: suspend (Long) -> List<BackupRow>,
        progress: (Int, Int) -> Unit = { _, _ -> }) =
        write({ sink.writeString(it) }, total, page, progress)

    suspend fun write(write: (String) -> Unit, total: Int, page: suspend (Long) -> List<BackupRow>,
        progress: (Int, Int) -> Unit = { _, _ -> }) {
        progress(0, total)
        write("""{"format":"$FORMAT","version":$VERSION,"invoices":[""")
        var cursor = 0L
        var written = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = page(cursor)
            if (rows.isEmpty()) break
            rows.forEachIndexed { index, row ->
                currentCoroutineContext().ensureActive()
                if (written != 0 || index != 0) write(",")
                write("""{"raw":${quote(row.raw)},"checkedAt":${row.scannedAt},"invoiceNumber":${quote(row.invoiceNumber)},"category":${row.category},"note":${quote(row.note)}}""")
            }
            written += rows.size
            cursor = rows.last().id
            progress(written, total)
        }
        write("]}")
    }

    private fun quote(value: String) = JsonPrimitive(value).toString()
        .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    /** The caller owns the transaction so any invalid or cancelled import rolls back. */
    suspend fun restore(json: BackupJsonInput, progress: (Int) -> Unit = {},
        save: suspend (Invoice, Long, InvoiceAnnotation) -> Boolean): ImportSummary {
        val seen = mutableSetOf<String>()
        var format: String? = null
        var version: Long? = null
        var added = 0
        var duplicates = 0
        var annotated = false
        json.beginObject()
        while (json.hasNext()) {
            currentCoroutineContext().ensureActive()
            val name = json.nextName()
            valid(seen.add(name))
            when (name) {
                "format" -> format = json.nextString()
                "version" -> version = json.nextInteger()
                "invoices" -> {
                    json.beginArray()
                    while (json.hasNext()) {
                        currentCoroutineContext().ensureActive()
                        json.resetRecord()
                        val fields = mutableSetOf<String>()
                        var raw: String? = null
                        var checkedAt: Long? = null
                        var invoiceNumber = ""
                        var category = 0
                        var note = ""
                        json.beginObject()
                        while (json.hasNext()) {
                            val field = json.nextName()
                            valid(fields.add(field))
                            when (field) {
                                "raw" -> raw = json.nextString().also { valid(it.length <= 100_000) }
                                "checkedAt" -> checkedAt = json.nextInteger()
                                "invoiceNumber" -> {
                                    annotated = true
                                    invoiceNumber = json.nextString().also { valid(it.length <= 120) }
                                }
                                "category" -> {
                                    annotated = true
                                    val id = json.nextInteger()
                                    valid(InvoiceCategories.ids.any { it.toLong() == id })
                                    category = id.toInt()
                                }
                                "note" -> {
                                    annotated = true
                                    note = json.nextString().also { valid(it.length <= 2_000) }
                                }
                                else -> throw InvalidBackupException()
                            }
                        }
                        json.endObject()
                        valid(raw != null && checkedAt != null && checkedAt in 0..253402300799999L)
                        val invoice = ZatcaParser.parse(raw!!)
                        valid(invoice.error == null)
                        val duplicate = save(invoice, checkedAt!!, InvoiceAnnotation(invoiceNumber, category, note))
                        if (duplicate) duplicates++ else added++
                        if ((added + duplicates) % 100 == 0) progress(added + duplicates)
                    }
                    json.endArray()
                }
                else -> throw InvalidBackupException()
            }
        }
        json.endObject()
        valid(format == FORMAT && version in 1L..VERSION.toLong() && "invoices" in seen)
        valid(version != 1L || !annotated)
        valid(json.endOfDocument())
        progress(added + duplicates)
        return ImportSummary(added, duplicates)
    }

    private fun valid(value: Boolean) { if (!value) throw InvalidBackupException() }
}
