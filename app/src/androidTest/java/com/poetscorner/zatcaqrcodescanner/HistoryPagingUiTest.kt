package com.poetscorner.zatcaqrcodescanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.zatcaqrcodescanner.data.*
import com.poetscorner.zatcaqrcodescanner.ui.HistoryScreen
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPagingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingLoadsLaterPagesAndFilteringResetsTheList() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            db.scans().insertBatch((1..125).map { historyFixture(it, seller = "Seller $it") })
            val pager = compose.runOnIdle { HistoryPager(db.scans(), scope) }
            var opened: Long? = null
            compose.setContent {
                val page by pager.state.collectAsState()
                MaterialTheme {
                    HistoryScreen(page, page.filter.query, { pager.setFilter(HistoryFilter(it)) },
                        onLoadMore = pager::loadMore, onRetry = pager::refresh,
                        onOpen = { opened = it.id }, onDelete = {})
                }
            }
            compose.waitUntil(10000) { !pager.state.value.loading && pager.state.value.entries.size == 50 }
            compose.onNodeWithTag("history_list").performScrollToIndex(45)
            compose.waitUntil(10000) { pager.state.value.entries.size == 100 }
            compose.onNodeWithTag("history_list").performScrollToIndex(95)
            compose.waitUntil(10000) { pager.state.value.entries.size == 125 }
            compose.onNodeWithTag("history_list").performScrollToIndex(126)
            compose.onNodeWithText("Seller 1").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1L, opened) }
            compose.runOnIdle { pager.setFilter(HistoryFilter("125")) }
            compose.waitUntil(10000) { !pager.state.value.loading && pager.state.value.matching == 1 }
            compose.onNodeWithText("Seller 125").assertIsDisplayed()
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }
}
