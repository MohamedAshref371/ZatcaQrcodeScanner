package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ExactAmount

/** Read the count, card range and exact sum in the caller's database snapshot. */
suspend fun readHistoryOverview(
    filter: HistoryFilter,
    rowLimit: Int,
    count: suspend (BoundQuery) -> HistoryCounts,
    summaries: suspend (BoundQuery) -> List<HistorySummary>,
    amounts: suspend (BoundQuery) -> List<HistoryAmount>,
): HistoryOverview {
    require(rowLimit > 0)
    val queries = HistoryQueries(filter)
    val counts = count(queries.counts())
    val rows = mutableListOf<HistorySummary>()
    while (rows.size < minOf(rowLimit, counts.matching)) {
        val last = rows.lastOrNull()?.let { HistoryCursor(it.scannedAt, it.id) }
        val page = summaries(queries.page(last, minOf(500, rowLimit - rows.size)))
        if (page.isEmpty()) break
        rows.addAll(page)
    }
    var amount = ExactAmount.ZERO
    var cursor: HistoryCursor? = null
    while (true) {
        val batch = amounts(queries.amounts(cursor))
        if (batch.isEmpty()) break
        for (row in batch) {
            val value = ExactAmount.parseInvoiceAmount(row.total) ?: return HistoryOverview(counts, rows, null)
            amount += value
        }
        val last = batch.last()
        cursor = HistoryCursor(last.scannedAt, last.id)
    }
    return HistoryOverview(counts, rows, amount)
}
