package com.poetscorner.zatcaqrcodescanner.data

import org.json.JSONArray
import org.json.JSONObject
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser

object HistoryExporter {
    fun json(rows: List<ScanEntity>): String = JSONArray().apply {
        rows.map { it.revalidated() }.forEach { row -> put(JSONObject().apply {
            put("id", row.id); put("scannedAt", row.scannedAt); put("raw", row.raw)
            put("seller", row.seller); put("localChecksPassed", row.passed); put("phase", row.phase)
            put("invoiceType", row.invoiceType)
            put("fields", JSONArray().apply { ZatcaParser.parse(row.raw).fields.forEach { f ->
                put(JSONObject().put("tag", f.tag).put("name", ZatcaParser.names[f.tag]).put("value", f.value))
            } })
        }) }
    }.toString(2)
    fun csv(rows: List<ScanEntity>): String {
        fun quote(value: String): String {
            // Prevent spreadsheet formula execution in exported untrusted QR text.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return "\uFEFFid,scannedAt,seller,phase,localChecksPassed,raw,invoiceType\r\n" + rows.map { it.revalidated() }.joinToString("\r\n") {
            listOf(it.id.toString(), it.scannedAt.toString(), it.seller, it.phase.toString(), it.passed.toString(), it.raw, it.invoiceType).joinToString(",", transform = ::quote)
        }
    }
}
