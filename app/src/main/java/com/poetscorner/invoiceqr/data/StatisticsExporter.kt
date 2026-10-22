package com.poetscorner.invoiceqr.data

import java.io.Writer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object StatisticsExporter {
    /** Export the displayed snapshot in its current order, with machine-readable exact amounts. */
    suspend fun write(writer: Writer, result: StatisticsResult, headers: List<String>, summaryTitle: String,
        title: (StatisticsKey) -> String) {
        require(headers.size == 6)
        fun quote(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        fun line(values: List<String>) { writer.write(values.joinToString(",", postfix = "\r\n", transform = ::quote)) }
        fun row(label: String, totals: StatisticsTotals) = line(listOf(label, totals.count.toString(),
            if (totals.count > 0 && totals.count == totals.missingTotal) "" else totals.total.toPlainString(),
            if (totals.count > 0 && totals.count == totals.missingTax) "" else totals.tax.toPlainString(),
            totals.missingTotal.toString(), totals.missingTax.toString()))
        writer.write("\uFEFF")
        line(headers)
        for (group in result.groups) {
            currentCoroutineContext().ensureActive()
            row(title(group.key), group.totals)
        }
        row(summaryTitle, result.summary)
    }
}
