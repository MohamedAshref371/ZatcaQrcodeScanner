package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.io.Sink
import java.io.Writer

object HistoryExporter {
    suspend fun write(dao: ScanDao, filter: HistoryFilter, sink: Sink, csv: Boolean, names: Map<Int, String>,
        categoryName: (Int) -> String = { it.toString() }, progress: (Int, Int) -> Unit = { _, _ -> }) =
        HistoryExports.write(dao.exportSource(), filter, sink, csv, names, categoryName, progress)

    suspend fun write(dao: ScanDao, filter: HistoryFilter, writer: Writer, csv: Boolean, names: Map<Int, String>,
        categoryName: (Int) -> String = { it.toString() }, progress: (Int, Int) -> Unit = { _, _ -> }) =
        HistoryExports.write(dao.exportSource(), filter, writer::write, csv, names, categoryName, progress)

    fun json(rows: List<ScanEntity>, names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() }): String =
        HistoryExports.json(rows.map { it.exportInvoice() }, names, categoryName)

    fun csv(rows: List<ScanEntity>, names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() }): String =
        HistoryExports.csv(rows.map { it.exportInvoice() }, names, categoryName)

    private fun ScanDao.exportSource() = object : HistoryExportSource {
        override suspend fun counts(query: BoundQuery) = this@exportSource.counts(query)
        override suspend fun hasHash(query: BoundQuery) = this@exportSource.hasHash(query)
        override suspend fun exportRows(query: BoundQuery) = this@exportSource.exportRows(query)
    }

    private fun ScanEntity.exportInvoice(): ExportInvoice {
        val invoice = ZatcaParser.parse(raw)
        return ExportInvoice(id, scannedAt, invoice.value(1), invoice.value(2), invoice.value(3),
            invoice.value(4), invoice.value(5), invoice.value(6), invoiceNumber, category, note)
    }
}
