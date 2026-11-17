package com.poetscorner.invoiceqr.data

import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object StatisticsExporter {
    suspend fun write(sink: Sink, result: StatisticsResult, headers: List<String>, summaryTitle: String,
        title: (StatisticsKey) -> String) =
        write({ sink.writeString(it) }, result, headers, summaryTitle, title)

    /** Export the displayed snapshot in its current order, with machine-readable exact amounts. */
    suspend fun write(write: (String) -> Unit, result: StatisticsResult, headers: List<String>, summaryTitle: String,
        title: (StatisticsKey) -> String) {
        require(headers.size == 6)
        val columns = buildList {
            addAll(0..3)
            if (result.summary.missingTotal != 0 || result.groups.any { it.totals.missingTotal != 0 }) add(4)
            if (result.summary.missingTax != 0 || result.groups.any { it.totals.missingTax != 0 }) add(5)
        }
        fun quote(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        fun line(values: List<String>) {
            write(columns.joinToString(",", postfix = "\r\n") { quote(values[it]) })
        }
        fun row(label: String, totals: StatisticsTotals) = line(listOf(label, totals.count.toString(),
            if (totals.count > totals.ignoredCount && totals.count - totals.ignoredCount == totals.missingTotal) "" else totals.total.toPlainString(),
            if (totals.count > totals.ignoredCount && totals.count - totals.ignoredCount == totals.missingTax) "" else totals.tax.toPlainString(),
            totals.missingTotal.toString(), totals.missingTax.toString()))
        write("\uFEFF")
        line(headers)
        for (group in result.groups) {
            currentCoroutineContext().ensureActive()
            row(title(group.key), group.totals)
        }
        row(summaryTitle, result.summary)
    }
}
