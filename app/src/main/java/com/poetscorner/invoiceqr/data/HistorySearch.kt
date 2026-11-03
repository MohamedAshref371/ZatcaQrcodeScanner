package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ZatcaParser
import java.time.LocalDate
import java.time.ZoneId
import java.io.Serializable

enum class HistoryDateSource { INVOICE, SCAN }

/** Snapshot taken when export starts, retained while the system file picker is open. */
data class HistoryFilter(val query: String = "", val fromDay: Long? = null, val toDay: Long? = null,
    val dateSource: HistoryDateSource = HistoryDateSource.INVOICE,
    val zoneId: String = ZoneId.systemDefault().id,
    val statisticsSelection: StatisticsSelection? = null,
    val category: Int? = null,
    val annotationQuery: String = "") : Serializable {
    fun apply(entries: List<HistoryEntry>): List<HistoryEntry> =
        filterHistory(entries, query, fromDay, toDay, ZoneId.of(zoneId), dateSource).let { rows ->
            val selected = statisticsSelection
            val annotationTerm = annotationQuery.trim()
            rows.filter { entry ->
                (selected == null || selected.matches(entry.scan.seller, entry.vat, entry.invoiceDay)) &&
                    (category == null || entry.scan.category == category) &&
                    (annotationTerm.isEmpty() || entry.scan.invoiceNumber.contains(annotationTerm, ignoreCase = true) ||
                        entry.scan.note.contains(annotationTerm, ignoreCase = true))
            }
        }
}

data class HistoryEntry(val scan: ScanEntity, val vat: String, val invoiceDay: Long? = null, val total: String = "") {
    companion object {
        fun from(scan: ScanEntity): HistoryEntry {
            val invoice = ZatcaParser.parse(scan.raw)
            return HistoryEntry(scan.copy(passed = invoice.passed,
                phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name), invoice.value(2),
                runCatching { ZatcaParser.parseTimestamp(invoice.value(3)).toLocalDate().toEpochDay() }.getOrNull(),
                invoice.value(4))
        }
    }
}

/** Invoice dates use the date written on the invoice; scan dates use the device's local zone. */
fun filterHistory(entries: List<HistoryEntry>, query: String, fromDay: Long?, toDay: Long?,
    zone: ZoneId = ZoneId.systemDefault(), dateSource: HistoryDateSource = HistoryDateSource.SCAN): List<HistoryEntry> {
    val term = query.trim()
    val vatTerm = term.map { character ->
        if (character.isDigit()) Character.digit(character, 10).digitToChar() else character
    }.joinToString("")
    val start = fromDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(zone).toInstant().toEpochMilli() }
    val end = toDay?.let { LocalDate.ofEpochDay(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    return entries.filter { entry ->
        val matchesDate = when {
            fromDay == null && toDay == null -> true
            dateSource == HistoryDateSource.INVOICE -> entry.invoiceDay?.let { day ->
                (fromDay == null || day >= fromDay) && (toDay == null || day <= toDay)
            } == true
            else -> (start == null || entry.scan.scannedAt >= start) && (end == null || entry.scan.scannedAt < end)
        }
        (term.isEmpty() || entry.scan.seller.contains(term, ignoreCase = true) || entry.vat.contains(vatTerm)) &&
            matchesDate
    }
}
