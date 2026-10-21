package com.poetscorner.zatcaqrcodescanner

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPagerTest {
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
            withContext(Dispatchers.Main) { pager.setFilter(HistoryFilter("Seller 125")) }
            val one = ready(1)
            db.scans().delete(one.entries.single().id)
            val deleted = withTimeout(10000) { pager.state.first { !it.loading && it.total == 124 } }
            assertFalse(deleted.canExport)
            assertEquals(0, deleted.matching)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }
}
