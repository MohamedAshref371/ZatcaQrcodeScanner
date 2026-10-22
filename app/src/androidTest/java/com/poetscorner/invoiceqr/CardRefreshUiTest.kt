package com.poetscorner.invoiceqr

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.ui.HistoryScreen
import com.poetscorner.invoiceqr.ui.StatisticsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardRefreshUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historySlotStaysVisibleAndOpensItsReplacementInvoice() {
        val original = HistorySummary(1, "Old seller", "3001", "115.00", 1, true, 1)
        val entries = (0 until 10000).map { index -> original.copy(id = index.toLong() + 1,
            seller = if (index == 400) "Old seller" else "Seller $index") }
        val page = mutableStateOf(HistoryPageState(entries = entries, total = 10000, matching = 10000, loading = false))
        var opened: Long? = null
        compose.setContent { MaterialTheme {
            HistoryScreen(page.value, "", {}, {}, {}, onOpen = { opened = it.id }, onDelete = {})
        } }
        compose.onNodeWithTag("history_list").performScrollToIndex(405)
        val slotId = compose.onNodeWithTag("history_card_400").fetchSemanticsNode().id
        compose.runOnIdle { page.value = page.value.copy(loading = true) }
        compose.onNodeWithText("Old seller").assertIsDisplayed()
        compose.onNodeWithTag("history_card_400").assertIsNotEnabled()
        compose.runOnIdle {
            page.value = page.value.copy(entries = entries.mapIndexed { index, row ->
                row.copy(id = row.id + 10000, seller = if (index == 400) "New seller" else "Replacement $index")
            }, loading = false)
        }
        compose.onNodeWithTag("history_card_400").assertIsDisplayed().performClick()
        assertEquals(slotId, compose.onNodeWithTag("history_card_400").fetchSemanticsNode().id)
        compose.runOnIdle {
            assertEquals(10401L, opened)
        }
        compose.onNodeWithText("New seller").assertIsDisplayed()
        compose.onNodeWithText("Old seller").assertDoesNotExist()
    }

    @Test fun pendingSearchDoesNotRemoveTheCardBeforeThePagerCatchesUp() {
        val row = HistorySummary(1, "Pending search seller", "3001", "115.00", 1, true, 1)
        val page = mutableStateOf(HistoryPageState(entries = listOf(row), total = 1, matching = 1, loading = false))
        compose.setContent { MaterialTheme {
            HistoryScreen(page.value, page.value.filter.query, {}, {}, {}, onOpen = {}, onDelete = {})
        } }
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("history_card_0"))
        val slotId = compose.onNodeWithTag("history_card_0").fetchSemanticsNode().id
        compose.runOnIdle { page.value = page.value.forDisplay(HistoryFilter("next search")) }
        compose.onNodeWithText("Pending search seller").assertIsDisplayed()
        compose.onNodeWithTag("history_card_0").assertIsNotEnabled()
        assertEquals(slotId, compose.onNodeWithTag("history_card_0").fetchSemanticsNode().id)
    }

    @Test fun statisticsSlotSurvivesLoadingFailureAndDifferentGroupResults() {
        val totals = StatisticsTotals(count = 1)
        val oldKey = StatisticsKey(text = "Old group")
        val newKey = StatisticsKey(text = "New group")
        val groups = (0 until 10000).map { index -> StatisticsGroup(
            if (index == 400) oldKey else StatisticsKey(text = "Group $index"), totals) }
        val summary = StatisticsTotals(count = 10000)
        val page = mutableStateOf(StatisticsState(result = StatisticsResult(summary, groups), loading = false))
        var opened: StatisticsKey? = null
        compose.setContent { MaterialTheme {
            StatisticsScreen(page.value, {}, {}, onOpenGroup = { opened = it })
        } }
        compose.onNodeWithTag("statistics_list").performScrollToIndex(404)
        val slotId = compose.onNodeWithTag("statistics_card_400").fetchSemanticsNode().id
        compose.runOnIdle {
            page.value = page.value.copy(request = StatisticsRequest(grouping = StatisticsGrouping.MONTH), loading = true)
        }
        compose.onNodeWithText("Old group").assertIsDisplayed()
        compose.runOnIdle { page.value = page.value.copy(loading = false, failed = true) }
        compose.onNodeWithText("Old group").assertIsDisplayed()
        compose.runOnIdle {
            page.value = StatisticsState(result = StatisticsResult(summary, groups.mapIndexed { index, group ->
                group.copy(key = if (index == 400) newKey else StatisticsKey(text = "Replacement $index"))
            }), loading = false)
        }
        compose.onNodeWithTag("statistics_card_400").assertIsDisplayed().performClick()
        assertEquals(slotId, compose.onNodeWithTag("statistics_card_400").fetchSemanticsNode().id)
        compose.runOnIdle {
            assertEquals(newKey, opened)
        }
        compose.onNodeWithText("New group").assertIsDisplayed()
        compose.onNodeWithText("Old group").assertDoesNotExist()
    }
}
