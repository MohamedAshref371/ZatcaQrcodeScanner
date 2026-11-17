package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ZatcaParser
import java.time.ZoneId

data class HistoryEntry(val scan: ScanEntity, val vat: String, val invoiceDay: Long? = null, val total: String = "") {
    companion object {
        fun from(scan: ScanEntity): HistoryEntry {
            val invoice = ZatcaParser.parse(scan.raw)
            val details = InvoiceDetails.from(invoice, scan.seller)
            return HistoryEntry(scan.copy(passed = invoice.passed,
                phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name),
                details.vat, details.invoiceDay, details.total)
        }
    }
}

fun HistoryFilter.apply(entries: List<HistoryEntry>): List<HistoryEntry> = select(entries) {
    HistorySearchFields(it.scan.seller, it.vat, it.scan.scannedAt, it.invoiceDay, it.scan.category,
        it.scan.invoiceNumber, it.scan.note)
}

fun filterHistory(entries: List<HistoryEntry>, query: String, fromDay: Long?, toDay: Long?,
    zone: ZoneId = ZoneId.systemDefault(), dateSource: HistoryDateSource = HistoryDateSource.SCAN): List<HistoryEntry> =
    HistoryFilter(query, fromDay, toDay, dateSource, zone.id).apply(entries)
