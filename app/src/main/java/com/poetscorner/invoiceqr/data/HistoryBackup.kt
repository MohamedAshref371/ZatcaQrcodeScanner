package com.poetscorner.invoiceqr.data

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CancellationException
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.io.EOFException
import java.io.FilterReader
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

object HistoryBackup {
    const val MAX_BYTES = HistoryBackupFormat.MAX_BYTES

    suspend fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long = MAX_BYTES) =
        HistoryBackupFormat.copyLimited(input.asSource(), output.asSink(), maxBytes)

    suspend fun write(db: ScanDatabase, output: Sink, progress: (Int, Int) -> Unit = { _, _ -> }) {
        db.readSnapshot {
            val dao = db.scans()
            HistoryBackupFormat.write(output, dao.count(), dao::backupRows, progress)
        }
    }

    suspend fun write(db: ScanDatabase, output: Writer, progress: (Int, Int) -> Unit = { _, _ -> }) {
        db.readSnapshot {
            val dao = db.scans()
            HistoryBackupFormat.write(output::write, dao.count(), dao::backupRows, progress)
            output.flush()
        }
    }

    suspend fun restore(db: ScanDatabase, input: Reader, progress: (Int) -> Unit = {}): ImportSummary {
        try {
            return db.writeTransaction {
                HistoryBackupFormat.restore(AndroidBackupJsonInput(input), progress) { invoice, timestamp, annotation ->
                    db.scans().saveIfNew(ScanEntity.from(invoice, timestamp).copy(
                        invoiceNumber = annotation.invoiceNumber, category = annotation.category,
                        note = annotation.note)).alreadyExists
                }
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
}

private class AndroidBackupJsonInput(input: Reader) : BackupJsonInput {
    private val bounded = RecordReader(input)
    private val json = JsonReader(bounded)

    override fun beginObject() = json.beginObject()
    override fun endObject() = json.endObject()
    override fun beginArray() = json.beginArray()
    override fun endArray() = json.endArray()
    override fun hasNext() = json.hasNext()
    override fun nextName(): String = json.nextName()
    override fun resetRecord() = bounded.resetRecord()
    override fun endOfDocument() = json.peek() == JsonToken.END_DOCUMENT

    override fun nextString(): String {
        if (json.peek() != JsonToken.STRING) throw InvalidBackupException()
        return json.nextString()
    }

    override fun nextInteger(): Long {
        if (json.peek() != JsonToken.NUMBER) throw InvalidBackupException()
        return json.nextString().toLongOrNull() ?: throw InvalidBackupException()
    }
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
