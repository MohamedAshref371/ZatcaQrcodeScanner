package com.poetscorner.zatcaqrcodescanner.data

import org.json.JSONArray
import org.json.JSONObject
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser

object HistoryExporter {
    private fun fields(row: ScanEntity): Map<Int, String> {
        val invoice = ZatcaParser.parse(row.raw)
        return (1..6).associateWith { invoice.value(it) }
    }

    fun json(rows: List<ScanEntity>, names: Map<Int, String>): String = JSONArray().apply {
        rows.forEach { row ->
            put(JSONObject().apply {
                fields(row).forEach { (tag, value) ->
                    if (tag != 6 || value.isNotBlank()) put(names.getValue(tag), value)
                }
            })
        }
    }.toString(2)
    fun csv(rows: List<ScanEntity>, names: Map<Int, String>): String {
        fun quote(value: String): String {
            // Prevent spreadsheet formula execution in exported untrusted QR text.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val invoices = rows.map(::fields)
        val tags = if (invoices.any { it.getValue(6).isNotBlank() }) (1..6) else (1..5)
        val header = tags.joinToString(",") { quote(names.getValue(it)) }
        return "\uFEFF$header\r\n" + invoices.joinToString("\r\n") { invoice ->
            tags.joinToString(",") { quote(invoice.getValue(it)) }
        }
    }
}
