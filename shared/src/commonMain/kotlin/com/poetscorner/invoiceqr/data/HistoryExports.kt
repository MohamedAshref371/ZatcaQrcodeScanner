package com.poetscorner.invoiceqr.data

import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface HistoryExportSource {
    suspend fun counts(query: BoundQuery): HistoryCounts
    suspend fun hasHash(query: BoundQuery): Boolean
    suspend fun exportRows(query: BoundQuery): List<ExportInvoice>
}

object HistoryExports {
    suspend fun write(source: HistoryExportSource, filter: HistoryFilter, sink: Sink, csv: Boolean,
        names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() },
        progress: (Int, Int) -> Unit = { _, _ -> }) =
        write(source, filter, { sink.writeString(it) }, csv, names, categoryName, progress)

    /** The caller holds a database transaction so all pages and the CSV header share one snapshot. */
    suspend fun write(source: HistoryExportSource, filter: HistoryFilter, write: (String) -> Unit, csv: Boolean, names: Map<Int, String>,
        categoryName: (Int) -> String = { it.toString() },
        progress: (Int, Int) -> Unit = { _, _ -> }) {
        val queries = HistoryQueries(filter)
        val total = source.counts(queries.counts()).matching
        var written = 0
        progress(0, total)
        val columns = listOf(7) + (if (csv && source.hasHash(queries.hashExists())) (1..6).toList() else (1..5).toList()) + listOf(8, 9)
        if (csv) write("\uFEFF" + columns.joinToString(",") { quote(names.getValue(it)) } + "\r\n")
        else write("[")
        var cursor: HistoryCursor? = null
        var first = true
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = source.exportRows(queries.export(cursor))
            if (rows.isEmpty()) break
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                if (csv) {
                    if (!first) write("\r\n")
                    val fields = row.fields(categoryName(row.category))
                    write(columns.joinToString(",") { quote(fields[fieldIndex(it)]) })
                } else {
                    write(if (first) "\n  {" else ",\n  {")
                    val fields = row.fields(categoryName(row.category))
                    val included = listOf(7) + (if (row.hash.isBlank()) (1..5).toList() else (1..6).toList()) + listOf(8, 9)
                    write(included.joinToString(",\n", prefix = "\n") { tag ->
                        "    " + quoteExportJson(names.getValue(tag)) + ": " + quoteExportJson(fields[fieldIndex(tag)])
                    })
                    write("\n  }")
                }
                first = false
            }
            rows.last().let { cursor = HistoryCursor(it.scannedAt, it.id) }
            written += rows.size
            progress(written, total)
        }
        if (!csv) write(if (first) "]" else "\n]")
    }

    private fun fieldIndex(tag: Int) = when (tag) { 7 -> 0; 8 -> 7; 9 -> 8; else -> tag }

    private fun quote(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    private fun fields(row: ExportInvoice, categoryName: (Int) -> String) =
        row.fields(categoryName(row.category))

    fun json(rows: List<ExportInvoice>, names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() }): String {
        if (rows.isEmpty()) return "[]"
        return rows.joinToString(",\n", prefix = "[\n", postfix = "\n]") { row ->
            val fields = fields(row, categoryName)
            val tags = listOf(7) + (if (row.hash.isBlank()) (1..5).toList() else (1..6).toList()) + listOf(8, 9)
            tags.joinToString(",\n", prefix = "  {\n", postfix = "\n  }") { tag ->
                "    " + quoteExportJson(names.getValue(tag)) + ": " + quoteExportJson(fields[fieldIndex(tag)])
            }
        }
    }

    fun csv(rows: List<ExportInvoice>, names: Map<Int, String>, categoryName: (Int) -> String = { it.toString() }): String {
        val tags = listOf(7) + (if (rows.any { it.hash.isNotBlank() }) (1..6).toList() else (1..5).toList()) + listOf(8, 9)
        val header = tags.joinToString(",") { quote(names.getValue(it)) }
        return "\uFEFF$header\r\n" + rows.joinToString("\r\n") { row ->
            val fields = fields(row, categoryName)
            tags.joinToString(",") { quote(fields[fieldIndex(it)]) }
        }
    }
}

private fun quoteExportJson(value: String) = JsonPrimitive(value).toString().replace("/", "\\/")
