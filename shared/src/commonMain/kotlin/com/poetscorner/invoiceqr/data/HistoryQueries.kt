package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ExactAmount
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.datetime.TimeZone

data class InvoiceDetails(val sellerSearch: String, val vat: String, val invoiceDate: String,
    val invoiceDay: Long?, val total: String, val tax: String, val hash: String, val hashPresent: Boolean = hash.isNotBlank()) {
    companion object {
        fun from(invoice: Invoice, seller: String) = InvoiceDetails(seller.lowercase(), invoice.value(2),
            invoice.value(3), runCatching { ZatcaParser.parseTimestamp(invoice.value(3)).local.date.toEpochDays() }.getOrNull(),
            invoice.value(4), invoice.value(5), invoice.value(6))
    }
}

data class HistorySummary(val id: Long, val seller: String, val vat: String, val total: String,
    val scannedAt: Long, val passed: Boolean, val phase: Int,
    val invoiceNumber: String = "", val category: Int = 0)
data class HistoryCounts(val total: Int, val matching: Int)
data class HistoryCursor(val scannedAt: Long, val id: Long)
data class HistoryAmount(val id: Long, val scannedAt: Long, val total: String)
data class HistoryOverview(val counts: HistoryCounts, val rows: List<HistorySummary>, val amount: ExactAmount?)
data class ExportInvoice(val id: Long, val scannedAt: Long, val seller: String, val vat: String,
    val invoiceDate: String, val total: String, val tax: String, val hash: String,
    val invoiceNumber: String, val category: Int, val note: String) {
    fun fields(categoryName: String): List<String> = listOf(invoiceNumber, seller, vat, invoiceDate,
        total, tax, hash, if (category == 0) "" else categoryName, note)
}

data class BoundQuery(val sql: String, val arguments: List<Any>)

/** Only bound parameters contain user input; date predicates can use the matching index. */
class HistoryQueries(private val filter: HistoryFilter) {
    private fun conditions(): Pair<MutableList<String>, MutableList<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        filter.statisticsSelection?.let {
            val (selectedClauses, selectedArgs) = statisticsConditions(it.request, it.key)
            clauses.addAll(selectedClauses); args.addAll(selectedArgs)
        }
        val term = filter.query.trim()
        if (term.isNotEmpty()) {
            val digits = term.normalizeDigits()
            clauses += "(instr(sellerSearch, ?) > 0 OR instr(vat, ?) > 0)"
            args += term.lowercase(); args += digits
        }
        filter.annotationQuery.trim().takeIf { it.isNotEmpty() }?.let { annotationTerm ->
            clauses += "(instr(lower(invoiceNumber), ?) > 0 OR instr(lower(note), ?) > 0)"
            val lowered = annotationTerm.lowercase()
            args += lowered; args += lowered
        }
        filter.category?.let { clauses += "category = ?"; args += it }
        if (filter.dateSource == HistoryDateSource.INVOICE) {
            filter.fromDay?.let { clauses += "invoiceDay >= ?"; args += it }
            filter.toDay?.let { clauses += "invoiceDay <= ?"; args += it }
        } else {
            val zone = TimeZone.of(filter.zoneId)
            filter.fromDay?.let { clauses += "scannedAt >= ?"; args += startOfDayMillis(it, zone) }
            filter.toDay?.let { clauses += "scannedAt < ?"; args += startOfDayMillis(it, zone, next = true) }
        }
        return clauses to args
    }
    private fun query(select: String, cursor: HistoryCursor? = null, limit: Int? = null, hashOnly: Boolean = false): BoundQuery {
        val (clauses, args) = conditions()
        if (cursor != null) {
            clauses += "(scannedAt < ? OR (scannedAt = ? AND id < ?))"
            args += cursor.scannedAt; args += cursor.scannedAt; args += cursor.id
        }
        if (hashOnly) clauses += "hashPresent = 1"
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        val suffix = if (limit == null) "" else {
            require(limit in 1..500)
            args += limit
            " ORDER BY scannedAt DESC, id DESC LIMIT ?"
        }
        return BoundQuery("SELECT $select FROM scans$where$suffix", args)
    }
    fun counts() = query("(SELECT COUNT(*) FROM scans) AS total, COUNT(*) AS matching")
    fun amounts(cursor: HistoryCursor? = null) = query("id, scannedAt, total", cursor, 200)
    fun page(cursor: HistoryCursor? = null, limit: Int = 50) = query("id, seller, vat, total, scannedAt, passed, phase, invoiceNumber, category", cursor, limit)
    fun export(cursor: HistoryCursor? = null, limit: Int = 200) = query("id, scannedAt, seller, vat, invoiceDate, total, tax, hash, invoiceNumber, category, note", cursor, limit)
    fun hashExists() = query("COUNT(*) > 0", hashOnly = true)
}
