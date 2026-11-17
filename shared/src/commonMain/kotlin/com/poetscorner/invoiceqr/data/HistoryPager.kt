package com.poetscorner.invoiceqr.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.poetscorner.invoiceqr.domain.ExactAmount

data class HistoryPageState(val filter: HistoryFilter = HistoryFilter(), val entries: List<HistorySummary> = emptyList(),
    val total: Int = 0, val matching: Int = 0, val loading: Boolean = true, val loadingMore: Boolean = false,
    val failed: Boolean = false, val amount: ExactAmount? = null, val displayedFilter: HistoryFilter = filter) {
    val canExport get() = !loading && !failed && entries.isNotEmpty() && matching > 0
    val hasMore get() = entries.size < matching

    /** The controls may change one frame before the pager receives the new filter. */
    fun forDisplay(activeFilter: HistoryFilter): HistoryPageState = if (filter == activeFilter) this
        else copy(filter = activeFilter, loading = true, loadingMore = false, failed = false)
}

interface HistoryPageSource {
    fun observeCounts(query: BoundQuery): Flow<HistoryCounts>
    suspend fun overview(filter: HistoryFilter, rowLimit: Int): HistoryOverview
    suspend fun summaries(query: BoundQuery): List<HistorySummary>
}

/** Keyset pages contain card fields only. Raw payloads are fetched only when a card is opened. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPager(private val source: HistoryPageSource, private val scope: CoroutineScope) {
    private data class Request(val filter: HistoryFilter, val revision: Long = 0)
    private val request = MutableStateFlow(Request(HistoryFilter()))
    private val mutableState = MutableStateFlow(HistoryPageState())
    val state: StateFlow<HistoryPageState> = mutableState.asStateFlow()
    private var generation = 0L

    init {
        scope.launch {
            request.flatMapLatest { req ->
                source.observeCounts(HistoryQueries(req.filter).counts()).map { req to it }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        if (request.value == req) mutableState.value = mutableState.value.copy(loading = false, loadingMore = false, failed = true)
                    }
            }.collectLatest { (req, _) ->
                if (request.value != req) return@collectLatest
                val currentGeneration = ++generation
                val previous = mutableState.value
                val rowLimit = if (previous.displayedFilter == req.filter) maxOf(50, previous.entries.size) else 50
                mutableState.value = previous.copy(filter = req.filter, loading = true, loadingMore = false, failed = false)
                try {
                    val overview = source.overview(req.filter, rowLimit)
                    if (generation == currentGeneration && request.value == req) {
                        mutableState.value = HistoryPageState(req.filter, overview.rows, overview.counts.total,
                            overview.counts.matching, loading = false, amount = overview.amount)
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
        mutableState.value = mutableState.value.copy(filter = filter, loading = true, loadingMore = false, failed = false)
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
                val rows = source.summaries(HistoryQueries(current.filter).page(HistoryCursor(last.scannedAt, last.id)))
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
