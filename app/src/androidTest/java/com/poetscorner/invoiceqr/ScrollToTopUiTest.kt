package com.poetscorner.invoiceqr

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import com.poetscorner.invoiceqr.data.StatisticsGroup
import com.poetscorner.invoiceqr.data.StatisticsKey
import com.poetscorner.invoiceqr.data.StatisticsResult
import com.poetscorner.invoiceqr.data.StatisticsState
import com.poetscorner.invoiceqr.data.StatisticsTotals
import com.poetscorner.invoiceqr.ui.HistoryScreen
import com.poetscorner.invoiceqr.ui.StatisticsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollToTopUiTest {
    @get:Rule val compose = createComposeRule()

    private fun assertButtonAppearsFarDownAndReturnsToTop(listTag: String) {
        compose.onNodeWithTag("scroll_to_top").assertDoesNotExist()
        compose.onNodeWithTag(listTag).performScrollToIndex(2)
        compose.onNodeWithTag("scroll_to_top").assertDoesNotExist()
        compose.onNodeWithTag(listTag).performScrollToIndex(25)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("scroll_to_top").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("scroll_to_top").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("scroll_to_top").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun history(groupTitle: String? = null) {
        val rows = (1..80).map { index ->
            HistorySummary(index.toLong(), "Seller $index", "300000000000003", "115.00",
                index.toLong(), true, 1)
        }
        compose.setContent { MaterialTheme {
            HistoryScreen(HistoryPageState(entries = rows, total = rows.size,
                matching = rows.size, loading = false), "", {}, {}, {}, {}, {},
                groupTitle = groupTitle)
        } }
        assertButtonAppearsFarDownAndReturnsToTop("history_list")
    }

    @Test fun historyListHasScrollToTop() = history()

    @Test fun statisticsGroupInvoiceListHasScrollToTop() = history("Group invoices")

    @Test fun statisticsGroupsHaveScrollToTop() {
        val groups = (1..80).map { index ->
            StatisticsGroup(StatisticsKey(text = "Seller $index"), StatisticsTotals(count = 1))
        }
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(result = StatisticsResult(
                StatisticsTotals(count = groups.size), groups), loading = false), {}, {})
        } }
        assertButtonAppearsFarDownAndReturnsToTop("statistics_list")
    }
}
