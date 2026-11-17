package com.poetscorner.invoiceqr.data

/** Every page is read from one consistent snapshot, including during history deletion/import. */
suspend fun ScanDatabase.statistics(request: StatisticsRequest): StatisticsResult = readSnapshot {
    readStatistics(request) { scans().statisticsRows(statisticsQuery(request, it)) }
}

/** Reads only the selected group, using the same filters and amount parser as the overview. */
suspend fun ScanDatabase.statisticsBreakdown(selection: StatisticsSelection): StatisticsGroupBreakdown = readSnapshot {
    require(selection.request.categoryCalculationActive)
    val originalRequest = selection.request.copy(grouping = StatisticsGrouping.CATEGORY)
    readStatistics(originalRequest) { scans().statisticsRows(statisticsQuery(selection.request, it, selection.key)) }
        .toBreakdown(selection.request)
}
