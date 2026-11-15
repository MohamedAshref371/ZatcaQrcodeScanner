package com.poetscorner.invoiceqr.data

import androidx.room.withTransaction
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.shared.BackupInvoice
import com.poetscorner.invoiceqr.shared.HistoryBackupCodec
import com.poetscorner.invoiceqr.shared.HistoryBackupReader
import com.poetscorner.invoiceqr.shared.InvalidHistoryBackup
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    const val MAX_BYTES = HistoryBackupCodec.MAX_BYTES

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
            output.write(HistoryBackupCodec.header())
            var cursor = 0L
            var written = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val rows = db.scans().backupRows(cursor)
                if (rows.isEmpty()) break
                for (row in rows) {
                    currentCoroutineContext().ensureActive()
                    output.write(HistoryBackupCodec.record(
                        BackupInvoice(row.raw, row.scannedAt, row.invoiceNumber, row.category, row.note),
                        first = written == 0,
                    ))
                    written++
                }
                cursor = rows.last().id
                progress(written, total)
            }
            output.write(HistoryBackupCodec.footer())
            output.flush()
        }
    }

    /** Uses one transaction: invalid, truncated or cancelled imports never leave partial rows. */
    suspend fun restore(db: ScanDatabase, input: Reader, progress: (Int) -> Unit = {}): ImportSummary {
        try {
            return db.withTransaction {
                val context = currentCoroutineContext()
                val buffer = CharArray(8192)
                val backup = HistoryBackupReader {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) null else buffer.concatToString(0, count)
                }
                var added = 0
                var duplicates = 0
                while (true) {
                    context.ensureActive()
                    val decoded = backup.next() ?: break
                    val row = decoded.record
                    val saved = db.scans().saveIfNew(ScanEntity.from(ZatcaParser.fromParsed(decoded.invoice), row.checkedAt)
                        .copy(invoiceNumber = row.invoiceNumber, category = row.category, note = row.note))
                    if (saved.alreadyExists) duplicates++ else added++
                    if ((added + duplicates) % 100 == 0) progress(added + duplicates)
                }
                progress(added + duplicates)
                ImportSummary(added, duplicates)
            }
        } catch (error: InvalidHistoryBackup) {
            throw InvalidBackupException(error)
        }
    }
}
