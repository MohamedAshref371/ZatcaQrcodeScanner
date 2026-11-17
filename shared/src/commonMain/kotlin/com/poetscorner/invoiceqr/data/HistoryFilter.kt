package com.poetscorner.invoiceqr.data

import kotlinx.datetime.TimeZone

enum class HistoryDateSource { INVOICE, SCAN }

/** Snapshot taken when export starts, retained while the system file picker is open. */
data class HistoryFilter(
    val query: String = "", val fromDay: Long? = null, val toDay: Long? = null,
    val dateSource: HistoryDateSource = HistoryDateSource.INVOICE,
    val zoneId: String = TimeZone.currentSystemDefault().id,
    val statisticsSelection: StatisticsSelection? = null,
    val category: Int? = null,
    val annotationQuery: String = "",
) : SavedStateValue {
    private companion object { const val serialVersionUID = 8326515410588034567L }

    fun <T> select(entries: List<T>, fields: (T) -> HistorySearchFields): List<T> {
        val term = query.trim()
        val vatTerm = term.normalizeDigits()
        val annotationTerm = annotationQuery.trim()
        val zone = TimeZone.of(zoneId)
        val start = if (dateSource == HistoryDateSource.SCAN) fromDay?.let { startOfDayMillis(it, zone) } else null
        val end = if (dateSource == HistoryDateSource.SCAN) toDay?.let { startOfDayMillis(it, zone, next = true) } else null
        return entries.filter {
            val row = fields(it)
            val dateMatches = when {
                fromDay == null && toDay == null -> true
                dateSource == HistoryDateSource.INVOICE -> row.invoiceDay?.let { day ->
                    (fromDay == null || day >= fromDay) && (toDay == null || day <= toDay)
                } == true
                else -> (start == null || row.scannedAt >= start) && (end == null || row.scannedAt < end)
            }
            dateMatches && (term.isEmpty() || row.seller.contains(term, ignoreCase = true) || row.vat.contains(vatTerm)) &&
                statisticsSelection?.matches(row.seller, row.vat, row.invoiceDay, row.category) != false &&
                (category == null || row.category == category) &&
                (annotationTerm.isEmpty() || row.invoiceNumber.contains(annotationTerm, ignoreCase = true) ||
                    row.note.contains(annotationTerm, ignoreCase = true))
        }
    }
}

data class HistorySearchFields(
    val seller: String, val vat: String, val scannedAt: Long, val invoiceDay: Long?,
    val category: Int, val invoiceNumber: String, val note: String,
)
