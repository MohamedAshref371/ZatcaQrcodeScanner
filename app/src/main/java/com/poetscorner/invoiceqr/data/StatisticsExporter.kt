package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.shared.InvoiceStatisticsCsv
import java.io.Writer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object StatisticsExporter {
    /** Export the displayed snapshot in its current order, with machine-readable exact amounts. */
    suspend fun write(writer: Writer, result: StatisticsResult, headers: List<String>, summaryTitle: String,
        title: (StatisticsKey) -> String) {
        require(headers.size == 6)
        val document = InvoiceStatisticsCsv(headers,
            result.summary.missingTotal != 0 || result.groups.any { it.totals.missingTotal != 0 },
            result.summary.missingTax != 0 || result.groups.any { it.totals.missingTax != 0 })
        fun row(label: String, totals: StatisticsTotals) = writer.write(document.record(label, totals.count,
            totals.total.toPlainString(), totals.tax.toPlainString(), totals.missingTotal, totals.missingTax,
            totals.ignoredCount))
        writer.write(document.header())
        for (group in result.groups) {
            currentCoroutineContext().ensureActive()
            row(title(group.key), group.totals)
        }
        row(summaryTitle, result.summary)
    }
}
