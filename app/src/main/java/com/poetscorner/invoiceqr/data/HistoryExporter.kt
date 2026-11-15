package com.poetscorner.invoiceqr.data

import org.json.JSONArray
import org.json.JSONObject
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.shared.InvoiceHistoryCsv
import java.io.Writer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun ExportInvoice.csvFields(categoryName: String): List<String> =
    listOf(seller, vat, invoiceDate, total, tax, hash, invoiceNumber,
        if (category == 0) "" else categoryName, note)

object HistoryExporter {
    /** The caller holds a database transaction so all pages and the CSV header share one snapshot. */
    suspend fun write(dao: ScanDao, filter: HistoryFilter, writer: Writer, csv: Boolean, names: Map<Int, String>,
        categoryName: (Int) -> String = { it.toString() },
        progress: (Int, Int) -> Unit = { _, _ -> }) {
        val queries = HistoryQueries(filter)
        val total = dao.counts(queries.counts()).matching
        var written = 0
        progress(0, total)
        val csvDocument = if (csv) InvoiceHistoryCsv((1..9).map(names::getValue), dao.hasHash(queries.hashExists())) else null
        if (csvDocument != null) writer.write(csvDocument.header())
        else writer.write("[")
        var cursor: HistoryCursor? = null
        var first = true
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = dao.exportRows(queries.export(cursor))
            if (rows.isEmpty()) break
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                if (csvDocument != null) {
                    if (!first) writer.write("\r\n")
                    writer.write(csvDocument.record(row.csvFields(categoryName(row.category))))
                } else {
                    writer.write(if (first) "\n  {" else ",\n  {")
                    val fields = row.fields(categoryName(row.category))
                    val included = listOf(7) + (if (row.hash.isBlank()) (1..5).toList() else (1..6).toList()) + listOf(8, 9)
                    writer.write(included.joinToString(",\n", prefix = "\n") { tag ->
                        "    " + JSONObject.quote(names.getValue(tag)) + ": " + JSONObject.quote(fields[fieldIndex(tag)])
                    })
                    writer.write("\n  }")
                }
                first = false
            }
            rows.last().let { cursor = HistoryCursor(it.scannedAt, it.id) }
            written += rows.size
            progress(written, total)
        }
        if (!csv) writer.write(if (first) "]" else "\n]")
    }

    private fun fieldIndex(tag: Int) = when (tag) { 7 -> 0; 8 -> 7; 9 -> 8; else -> tag }

    private fun ScanEntity.csvFields(categoryName: String): List<String> {
        val invoice = ZatcaParser.parse(raw)
        return (1..6).map(invoice::value) + listOf(invoiceNumber, if (category == 0) "" else categoryName, note)
    }

    private fun fields(row: ScanEntity, categoryName: (Int) -> String): Map<Int, String> {
        val invoice = ZatcaParser.parse(row.raw)
        return mapOf(7 to row.invoiceNumber) + (1..6).associateWith { invoice.value(it) } +
            mapOf(8 to if (row.category == 0) "" else categoryName(row.category), 9 to row.note)
    }

    fun json(rows: List<ScanEntity>, names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() }): String = JSONArray().apply {
        rows.forEach { row ->
            put(JSONObject().apply {
                fields(row, categoryName).forEach { (tag, value) ->
                    if (tag != 6 || value.isNotBlank()) put(names.getValue(tag), value)
                }
            })
        }
    }.toString(2)
    fun csv(rows: List<ScanEntity>, names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() }): String {
        val invoices = rows.map { it.csvFields(categoryName(it.category)) }
        val document = InvoiceHistoryCsv((1..9).map(names::getValue), invoices.any { it[5].isNotBlank() })
        return document.header() + invoices.joinToString("\r\n", transform = document::record)
    }
}
