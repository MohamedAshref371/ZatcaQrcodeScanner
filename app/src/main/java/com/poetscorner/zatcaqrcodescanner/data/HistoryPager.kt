package com.poetscorner.zatcaqrcodescanner.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryPageState(val filter: HistoryFilter = HistoryFilter(), val entries: List<HistorySummary> = emptyList(),
    val total: Int = 0, val matching: Int = 0, val loading: Boolean = true, val loadingMore: Boolean = false,
    val failed: Boolean = false) {
    val canExport get() = !loading && entries.isNotEmpty() && matching > 0
    val hasMore get() = entries.size < matching
}

/** Keyset pages contain card fields only. Raw payloads are fetched only when a card is opened. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPager(private val dao: ScanDao, private val scope: CoroutineScope) {
    private data class Request(val filter: HistoryFilter, val revision: Long = 0)
    private val request = MutableStateFlow(Request(HistoryFilter()))
    private val mutableState = MutableStateFlow(HistoryPageState())
    val state: StateFlow<HistoryPageState> = mutableState.asStateFlow()
    private var generation = 0L

    init {
        scope.launch {
            request.flatMapLatest { req ->
                dao.observeCounts(HistoryQueries(req.filter).counts()).map { req to it }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        if (request.value == req) mutableState.value = HistoryPageState(req.filter, loading = false, failed = true)
                    }
            }.collectLatest { (req, counts) ->
                if (request.value != req) return@collectLatest
                val currentGeneration = ++generation
                mutableState.value = HistoryPageState(req.filter, total = counts.total, matching = counts.matching)
                try {
                    val rows = dao.summaries(HistoryQueries(req.filter).page())
                    if (generation == currentGeneration && request.value == req) {
                        mutableState.value = HistoryPageState(req.filter, rows, counts.total, counts.matching, loading = false)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (generation == currentGeneration && request.value == req) {
                        mutableState.value = mutableState.value.copy(loading = false, failed = true)
                    }
                }
            }
        }
    }

    fun setFilter(filter: HistoryFilter) {
        if (request.value.filter == filter) return
        reset(filter)
    }
    fun refresh() = reset(request.value.filter)
    private fun reset(filter: HistoryFilter) {
        generation++
        mutableState.value = HistoryPageState(filter)
        request.value = Request(filter, request.value.revision + 1)
    }
    fun loadMore() {
        val current = mutableState.value
        if (current.loading || current.loadingMore || current.failed || !current.hasMore) return
        val last = current.entries.lastOrNull() ?: return
        val currentGeneration = generation
        val req = request.value
        mutableState.value = current.copy(loadingMore = true)
        scope.launch {
            try {
                val rows = dao.summaries(HistoryQueries(current.filter).page(HistoryCursor(last.scannedAt, last.id)))
                if (generation == currentGeneration && request.value == req) {
                    mutableState.value = current.copy(entries = current.entries + rows,
                        matching = if (rows.isEmpty()) current.entries.size else current.matching)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == currentGeneration && request.value == req) {
                    mutableState.value = current.copy(failed = true)
                }
            }
        }
    }
}
