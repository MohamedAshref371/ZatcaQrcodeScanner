package com.poetscorner.invoiceqr.data

import org.json.JSONArray
import org.json.JSONObject
import com.poetscorner.invoiceqr.domain.ZatcaParser
import java.io.Writer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object HistoryExporter {
    /** The caller holds a database transaction so all pages and the CSV header share one snapshot. */
    suspend fun write(dao: ScanDao, filter: HistoryFilter, writer: Writer, csv: Boolean, names: Map<Int, String>,
        categoryName: (Int) -> String = { it.toString() },
        progress: (Int, Int) -> Unit = { _, _ -> }) {
        val queries = HistoryQueries(filter)
        val total = dao.counts(queries.counts()).matching
        var written = 0
        progress(0, total)
        val columns = listOf(7) + (if (csv && dao.hasHash(queries.hashExists())) (1..6).toList() else (1..5).toList()) + listOf(8, 9)
        if (csv) writer.write("\uFEFF" + columns.joinToString(",") { quote(names.getValue(it)) } + "\r\n")
        else writer.write("[")
        var cursor: HistoryCursor? = null
        var first = true
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = dao.exportRows(queries.export(cursor))
            if (rows.isEmpty()) break
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                if (csv) {
                    if (!first) writer.write("\r\n")
                    val fields = row.fields(categoryName(row.category))
                    writer.write(columns.joinToString(",") { quote(fields[fieldIndex(it)]) })
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

    private fun quote(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
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
        val invoices = rows.map { fields(it, categoryName) }
        val tags = listOf(7) + (if (invoices.any { it.getValue(6).isNotBlank() }) (1..6).toList() else (1..5).toList()) + listOf(8, 9)
        val header = tags.joinToString(",") { quote(names.getValue(it)) }
        return "\uFEFF$header\r\n" + invoices.joinToString("\r\n") { invoice ->
            tags.joinToString(",") { quote(invoice.getValue(it)) }
        }
    }
}
