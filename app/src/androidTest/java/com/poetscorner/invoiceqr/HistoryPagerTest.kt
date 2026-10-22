package com.poetscorner.invoiceqr

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPagerTest {
    @Test fun refreshKeepsLoadedPagesAndFilterChangesKeepCardsUntilReady() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            db.scans().insertBatch((1..125).map { historyFixture(it, seller = "Seller $it") })
            val pager = withContext(Dispatchers.Main) { HistoryPager(db.scans(), scope) }
            suspend fun ready(size: Int) = withTimeout(10000) {
                pager.state.first { !it.loading && !it.loadingMore && it.entries.size == size }
            }
            ready(50)
            withContext(Dispatchers.Main) { pager.loadMore() }
            val loaded = ready(100)
            withContext(Dispatchers.Main) {
                pager.refresh()
                assertEquals(loaded.entries, pager.state.value.entries)
                assertTrue(pager.state.value.loading)
                assertFalse(pager.state.value.canExport)
            }
            ready(100)
            db.scans().delete(loaded.entries.first().id)
            val updated = withTimeout(10000) { pager.state.first { !it.loading && it.total == 124 } }
            assertEquals(100, updated.entries.size)
            assertFalse(updated.entries.any { it.id == loaded.entries.first().id })
            withContext(Dispatchers.Main) {
                pager.setFilter(HistoryFilter("absent"))
                assertEquals(updated.entries, pager.state.value.entries)
                assertEquals(updated.filter, pager.state.value.displayedFilter)
                assertFalse(pager.state.value.canExport)
            }
            val empty = ready(0)
            assertEquals(HistoryFilter("absent"), empty.displayedFilter)
            assertEquals(0, empty.matching)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }

    @Test fun pagesResetOnFilterChangesAndDatabaseUpdatesWithoutStaleExportButtons() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            db.scans().insertBatch((1..125).map { historyFixture(it, seller = "Seller $it") })
            val pager = withContext(Dispatchers.Main) { HistoryPager(db.scans(), scope) }
            suspend fun ready(size: Int) = withTimeout(10000) { pager.state.first { !it.loading && !it.loadingMore && it.entries.size == size } }
            val first = ready(50)
            assertEquals(125, first.matching)
            assertEquals("14375.00", first.amount?.toPlainString())
            assertTrue(first.canExport)
            withContext(Dispatchers.Main) { pager.loadMore(); pager.loadMore() }
            ready(100)
            withContext(Dispatchers.Main) { pager.loadMore() }
            val all = ready(125)
            assertEquals(125, all.entries.map { it.id }.distinct().size)
            assertFalse(all.hasMore)
            // This invoice was beyond the first page. SQL search must still find it.
            withContext(Dispatchers.Main) { pager.setFilter(HistoryFilter("Seller 1", historyBaseDay + 1, historyBaseDay + 1)) }
            val filtered = withTimeout(10000) { pager.state.first { !it.loading && it.filter.query == "Seller 1" } }
            assertTrue(filtered.entries.isNotEmpty())
            assertTrue(filtered.entries.any { it.seller == "Seller 1" })
            withContext(Dispatchers.Main) {
                pager.setFilter(HistoryFilter("Seller 125"))
                pager.setFilter(HistoryFilter("no match"))
                assertFalse(pager.state.value.canExport)
            }
            assertEquals(0, ready(0).matching)
            assertEquals("0.00", pager.state.value.amount?.toPlainString())
            withContext(Dispatchers.Main) { pager.setFilter(HistoryFilter("Seller 125")) }
            val one = ready(1)
            assertEquals("115.00", one.amount?.toPlainString())
            db.scans().delete(one.entries.single().id)
            val deleted = withTimeout(10000) { pager.state.first { !it.loading && it.total == 124 } }
            assertFalse(deleted.canExport)
            assertEquals(0, deleted.matching)
            assertEquals("0.00", deleted.amount?.toPlainString())
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }
}
