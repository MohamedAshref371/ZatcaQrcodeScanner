package com.poetscorner.invoiceqr.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.CoroutineScope

internal fun BoundQuery.sqlite() = SimpleSQLiteQuery(sql, arguments.toTypedArray())

fun ScanDao.observeCounts(query: BoundQuery) = observeCounts(query.sqlite())
suspend fun ScanDao.counts(query: BoundQuery) = counts(query.sqlite())
suspend fun ScanDao.summaries(query: BoundQuery) = summaries(query.sqlite())
suspend fun ScanDao.exportRows(query: BoundQuery) = exportRows(query.sqlite())
suspend fun ScanDao.hasHash(query: BoundQuery) = hasHash(query.sqlite())
suspend fun ScanDao.statisticsRows(query: BoundQuery) = statisticsRows(query.sqlite())

fun HistoryPager(dao: ScanDao, scope: CoroutineScope) = HistoryPager(object : HistoryPageSource {
    override fun observeCounts(query: BoundQuery) = dao.observeCounts(query)
    override suspend fun overview(filter: HistoryFilter, rowLimit: Int) = dao.overview(filter, rowLimit)
    override suspend fun summaries(query: BoundQuery) = dao.summaries(query)
}, scope)
