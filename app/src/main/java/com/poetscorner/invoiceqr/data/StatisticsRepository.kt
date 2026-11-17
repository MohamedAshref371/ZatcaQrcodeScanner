package com.poetscorner.invoiceqr.data

import androidx.room.withTransaction

/** Every page is read from one consistent snapshot, including during history deletion/import. */
internal suspend fun ScanDatabase.statistics(request: StatisticsRequest): StatisticsResult = withTransaction {
    readStatistics(request) { scans().statisticsRows(statisticsQuery(request, it)) }
}

/** Reads only the selected group, using the same filters and amount parser as the overview. */
internal suspend fun ScanDatabase.statisticsBreakdown(selection: StatisticsSelection): StatisticsGroupBreakdown = withTransaction {
    require(selection.request.categoryCalculationActive)
    val originalRequest = selection.request.copy(grouping = StatisticsGrouping.CATEGORY)
    readStatistics(originalRequest) { scans().statisticsRows(statisticsQuery(selection.request, it, selection.key)) }
        .toBreakdown(selection.request)
}
