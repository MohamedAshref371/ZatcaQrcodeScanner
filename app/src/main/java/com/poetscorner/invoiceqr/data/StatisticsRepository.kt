package com.poetscorner.invoiceqr.data

import androidx.room.withTransaction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Every page is read from one consistent snapshot, including during history deletion/import. */
internal suspend fun ScanDatabase.statistics(request: StatisticsRequest): StatisticsResult = withTransaction {
    val accumulator = StatisticsAccumulator(request)
    var afterId = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = scans().statisticsRows(statisticsQuery(request, afterId))
        if (page.isEmpty()) break
        page.forEach(accumulator::add)
        afterId = page.last().id
    }
    accumulator.finish()
}
