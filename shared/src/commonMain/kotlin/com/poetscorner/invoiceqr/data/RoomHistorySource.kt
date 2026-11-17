package com.poetscorner.invoiceqr.data

import androidx.room.RoomRawQuery
import kotlinx.coroutines.CoroutineScope

internal fun BoundQuery.roomQuery() = RoomRawQuery(sql) { statement ->
    arguments.forEachIndexed { index, value ->
        val parameter = index + 1
        when (value) {
            is String -> statement.bindText(parameter, value)
            is Int -> statement.bindLong(parameter, value.toLong())
            is Long -> statement.bindLong(parameter, value)
            else -> error("Unsupported SQLite binding type")
        }
    }
}

fun ScanDao.observeCounts(query: BoundQuery) = observeCounts(query.roomQuery())
suspend fun ScanDao.counts(query: BoundQuery) = counts(query.roomQuery())
suspend fun ScanDao.summaries(query: BoundQuery) = summaries(query.roomQuery())
suspend fun ScanDao.exportRows(query: BoundQuery) = exportRows(query.roomQuery())
suspend fun ScanDao.hasHash(query: BoundQuery) = hasHash(query.roomQuery())
suspend fun ScanDao.statisticsRows(query: BoundQuery) = statisticsRows(query.roomQuery())

fun HistoryPager(dao: ScanDao, scope: CoroutineScope) = HistoryPager(object : HistoryPageSource {
    override fun observeCounts(query: BoundQuery) = dao.observeCounts(query)
    override suspend fun overview(filter: HistoryFilter, rowLimit: Int) = dao.overview(filter, rowLimit)
    override suspend fun summaries(query: BoundQuery) = dao.summaries(query)
}, scope)
