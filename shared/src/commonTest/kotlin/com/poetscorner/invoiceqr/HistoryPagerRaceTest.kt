package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.ExactAmount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPagerRaceTest {
    @Test fun invalidationDuringLoadMoreRetainsTheRequestedPage() = runTest {
        val invalidations = MutableSharedFlow<HistoryCounts>(replay = 1)
        invalidations.emit(HistoryCounts(125, 125))
        val rows = (1..125).map { HistorySummary(it.toLong(), "Seller $it", "", "115.00", it.toLong(), true, 1) }
        val page = CompletableDeferred<List<HistorySummary>>()
        val pager = HistoryPager(object : HistoryPageSource {
            override fun observeCounts(query: BoundQuery) = invalidations
            override suspend fun overview(filter: HistoryFilter, rowLimit: Int) =
                HistoryOverview(HistoryCounts(125, 125), rows.take(rowLimit), ExactAmount.ZERO)
            override suspend fun summaries(query: BoundQuery) = page.await()
        }, backgroundScope)
        runCurrent()
        assertEquals(50, pager.state.value.entries.size)
        pager.loadMore()
        runCurrent()
        assertTrue(pager.state.value.loadingMore)
        // Room can deliver a queued invalidation after its first overview is already visible.
        invalidations.emit(HistoryCounts(125, 125))
        runCurrent()
        page.complete(rows.drop(50).take(50))
        runCurrent()
        assertEquals(100, pager.state.value.entries.size)
        assertFalse(pager.state.value.loadingMore)
        assertFalse(pager.state.value.loading)
        assertTrue(pager.state.value.canExport)
    }
}
