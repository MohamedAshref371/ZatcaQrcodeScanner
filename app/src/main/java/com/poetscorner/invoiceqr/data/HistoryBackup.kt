package com.poetscorner.invoiceqr.data

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.EOFException
import java.io.FilterReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

data class BackupRow(val id: Long, val raw: String, val scannedAt: Long,
    val invoiceNumber: String, val category: Int, val note: String)
data class ImportSummary(val added: Int, val duplicates: Int)
class InvalidBackupException(cause: Throwable? = null) : IOException("Invalid invoice history backup", cause)
class BackupTooLargeException : IOException("Backup exceeds 64 MiB")

/** Language-independent, versioned history format. Derived invoice fields are rebuilt on import. */
object HistoryBackup {
    private const val FORMAT = "invoiceqr-history-backup"
    private const val VERSION = 2
    const val MAX_BYTES = 64L * 1024 * 1024

    suspend fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long = MAX_BYTES) {
        val buffer = ByteArray(8192)
        var bytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count == -1) return
            bytes += count
            if (bytes > maxBytes) throw BackupTooLargeException()
            output.write(buffer, 0, count)
        }
    }

    suspend fun write(db: ScanDatabase, output: Writer, progress: (Int, Int) -> Unit = { _, _ -> }) {
        db.withTransaction {
            val total = db.scans().count()
            progress(0, total)
            val json = JsonWriter(output)
            json.beginObject().name("format").value(FORMAT).name("version").value(VERSION)
                .name("invoices").beginArray()
            var cursor = 0L
            var written = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val rows = db.scans().backupRows(cursor)
                if (rows.isEmpty()) break
                for (row in rows) {
                    currentCoroutineContext().ensureActive()
                    json.beginObject().name("raw").value(row.raw)
                        .name("checkedAt").value(row.scannedAt)
                        .name("invoiceNumber").value(row.invoiceNumber)
                        .name("category").value(row.category.toLong())
                        .name("note").value(row.note).endObject()
                }
                written += rows.size
                cursor = rows.last().id
                progress(written, total)
            }
            json.endArray().endObject()
            json.flush()
        }
    }

    /** Uses one transaction: invalid, truncated or cancelled imports never leave partial rows. */
    suspend fun restore(db: ScanDatabase, input: Reader, progress: (Int) -> Unit = {}): ImportSummary {
        try {
            return db.withTransaction {
                val bounded = RecordReader(input)
                val json = JsonReader(bounded)
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
                        "format" -> format = string(json)
                        "version" -> version = integer(json)
                        "invoices" -> {
                            json.beginArray()
                            while (json.hasNext()) {
                                currentCoroutineContext().ensureActive()
                                bounded.resetRecord()
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
                                        "raw" -> raw = string(json).also { valid(it.length <= 100_000) }
                                        "checkedAt" -> checkedAt = integer(json)
                                        "invoiceNumber" -> {
                                            annotated = true
                                            invoiceNumber = string(json).also { valid(it.length <= 120) }
                                        }
                                        "category" -> {
                                            annotated = true
                                            val id = integer(json)
                                            valid(id in InvoiceCategories.ids.first.toLong()..InvoiceCategories.ids.last.toLong())
                                            category = id.toInt()
                                        }
                                        "note" -> {
                                            annotated = true
                                            note = string(json).also { valid(it.length <= 2_000) }
                                        }
                                        else -> throw InvalidBackupException()
                                    }
                                }
                                json.endObject()
                                valid(raw != null && checkedAt != null && checkedAt in 0..253402300799999L)
                                val invoice = ZatcaParser.parse(raw!!)
                                valid(invoice.error == null)
                                val saved = db.scans().saveIfNew(ScanEntity.from(invoice, checkedAt!!)
                                    .copy(invoiceNumber = invoiceNumber, category = category, note = note))
                                if (saved.alreadyExists) duplicates++ else added++
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
                valid(json.peek() == JsonToken.END_DOCUMENT)
                progress(added + duplicates)
                ImportSummary(added, duplicates)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: android.util.MalformedJsonException) {
            throw InvalidBackupException(error)
        } catch (error: EOFException) {
            throw InvalidBackupException(error)
        } catch (error: IllegalStateException) {
            throw InvalidBackupException(error)
        } catch (error: NumberFormatException) {
            throw InvalidBackupException(error)
        }
    }

    private fun valid(value: Boolean) { if (!value) throw InvalidBackupException() }
    private fun string(json: JsonReader): String {
        valid(json.peek() == JsonToken.STRING)
        return json.nextString()
    }
    private fun integer(json: JsonReader): Long {
        valid(json.peek() == JsonToken.NUMBER)
        return json.nextString().toLongOrNull() ?: throw InvalidBackupException()
    }

    // JsonReader buffers a little ahead; allow ample space for one escaped 100k-character QR.
    // This also bounds malformed single-string allocations while keeping whole files streamed.
    private class RecordReader(reader: Reader) : FilterReader(reader) {
        private var remaining = 700_000
        fun resetRecord() { remaining = 700_000 }
        override fun read(buffer: CharArray, offset: Int, count: Int): Int {
            if (remaining <= 0) throw InvalidBackupException()
            val read = super.read(buffer, offset, minOf(count, remaining))
            if (read > 0) remaining -= read
            return read
        }
    }
}
