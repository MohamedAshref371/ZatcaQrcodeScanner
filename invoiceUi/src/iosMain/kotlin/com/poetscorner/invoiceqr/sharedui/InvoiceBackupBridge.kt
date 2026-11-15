package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.BackupInvoice
import com.poetscorner.invoiceqr.shared.HistoryBackupCodec
import com.poetscorner.invoiceqr.shared.HistoryBackupReader
import com.poetscorner.invoiceqr.shared.InvalidHistoryBackup
import com.poetscorner.invoiceqr.shared.normalizedInvoicePayload

data class InvoiceBackupRow(
    val raw: String,
    val checkedAt: Long,
    val invoiceNumber: String,
    val category: Int,
    val note: String,
)

data class InvoiceBackupRecord(
    val row: InvoiceBackupRow,
    val normalizedPayload: String,
    val seller: String,
    val vat: String,
    val total: String,
    val tax: String,
    val invoiceDay: String,
)

interface InvoiceBackupInput {
    fun readChunk(): String?
}

object InvoiceBackupWriter {
    val maxBytes get() = HistoryBackupCodec.MAX_BYTES
    fun header() = HistoryBackupCodec.header()
    fun record(row: InvoiceBackupRow, first: Boolean): String =
        HistoryBackupCodec.record(BackupInvoice(row.raw, row.checkedAt, row.invoiceNumber, row.category, row.note), first)
    fun footer() = HistoryBackupCodec.footer()
}

class InvoiceBackupReader(input: InvoiceBackupInput) {
    private val reader = HistoryBackupReader(input::readChunk)

    @Throws(InvalidHistoryBackup::class)
    fun next(): InvoiceBackupRecord? {
        val decoded = reader.next() ?: return null
        val (raw, checkedAt, number, category, note) = decoded.record
        val invoice = decoded.invoice
        return InvoiceBackupRecord(InvoiceBackupRow(raw, checkedAt, number, category, note),
            normalizedInvoicePayload(raw), invoice.value(1), invoice.value(2), invoice.value(4), invoice.value(5),
            invoiceHistoryDay(invoice))
    }
}
