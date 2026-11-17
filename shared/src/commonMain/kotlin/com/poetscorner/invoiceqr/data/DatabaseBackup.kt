package com.poetscorner.invoiceqr.data

import kotlinx.io.Sink

object DatabaseBackup {
    suspend fun write(db: ScanDatabase, output: Sink, progress: (Int, Int) -> Unit = { _, _ -> }) {
        db.readSnapshot {
            val dao = db.scans()
            HistoryBackupFormat.write(output, dao.count(), dao::backupRows, progress)
        }
    }
    suspend fun restore(db: ScanDatabase, input: BackupJsonInput, progress: (Int) -> Unit = {}): ImportSummary =
        db.writeTransaction {
            HistoryBackupFormat.restore(input, progress) { invoice, timestamp, annotation ->
                db.scans().saveIfNew(ScanEntity.from(invoice, timestamp).copy(
                    invoiceNumber = annotation.invoiceNumber, category = annotation.category,
                    note = annotation.note)).alreadyExists
            }
        }
}

fun ScanDao.exportSource(): HistoryExportSource = object : HistoryExportSource {
    override suspend fun counts(query: BoundQuery) = this@exportSource.counts(query)
    override suspend fun hasHash(query: BoundQuery) = this@exportSource.hasHash(query)
    override suspend fun exportRows(query: BoundQuery) = this@exportSource.exportRows(query)
}
