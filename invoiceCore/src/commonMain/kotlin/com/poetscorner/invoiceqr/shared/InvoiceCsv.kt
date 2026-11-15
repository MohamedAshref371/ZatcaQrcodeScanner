package com.poetscorner.invoiceqr.shared

private fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}

private fun csvLine(values: List<String>): String = values.joinToString(",", transform = ::csvCell)

/** Header and rows have the same order as Android's filtered history export. */
class InvoiceHistoryCsv(private val headings: List<String>, val hasHash: Boolean) {
    init { require(headings.size == 9) }

    private val columns = listOf(6) + (if (hasHash) (0..5).toList() else (0..4).toList()) + listOf(7, 8)

    fun header(): String = "\uFEFF" + csvLine(columns.map(headings::get)) + "\r\n"

    /** Values: seller, VAT, date, total, tax, hash, invoice number, category, note. */
    fun record(values: List<String>): String {
        require(values.size == 9)
        return csvLine(columns.map(values::get))
    }
}

/** Formats the displayed statistics snapshot without rounding its amount strings. */
class InvoiceStatisticsCsv(private val headings: List<String>, includeMissingTotal: Boolean,
                           includeMissingTax: Boolean) {
    init { require(headings.size == 6) }

    private val columns = listOf(0, 1, 2, 3) +
        (if (includeMissingTotal) listOf(4) else emptyList()) +
        (if (includeMissingTax) listOf(5) else emptyList())

    fun header(): String = "\uFEFF" + csvLine(columns.map(headings::get)) + "\r\n"

    fun record(label: String, count: Int, total: String, tax: String, missingTotal: Int,
               missingTax: Int, ignoredCount: Int): String {
        val included = count - ignoredCount
        val values = listOf(label, count.toString(),
            if (included > 0 && included == missingTotal) "" else total,
            if (included > 0 && included == missingTax) "" else tax,
            missingTotal.toString(), missingTax.toString())
        return csvLine(columns.map(values::get)) + "\r\n"
    }
}
