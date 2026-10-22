package com.poetscorner.invoiceqr

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.ui.StatisticsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun groupingSortingAndFiltersUpdateTheRequestWithoutLosingOtherChoices() {
        var request by mutableStateOf(StatisticsRequest())
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {})
        } }
        compose.onNodeWithText(text(R.string.stats_group_by)).performClick()
        compose.onNodeWithText(text(R.string.stats_quarter)).performClick()
        compose.runOnIdle { assertEquals(StatisticsGrouping.QUARTER, request.grouping) }
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasText(text(R.string.stats_sort_by)))
        compose.onNodeWithText(text(R.string.stats_sort_by)).performClick()
        compose.onNodeWithText(text(R.string.stats_count_label)).performClick()
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasText(text(R.string.stats_sort_direction)))
        compose.onNodeWithText(text(R.string.stats_sort_direction)).performClick()
        compose.onNodeWithText(text(R.string.stats_descending)).performClick()
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasText(text(R.string.history_filter_title)))
        compose.onNodeWithText(text(R.string.history_filter_title)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tag_seller))).performTextInput("Market")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tag_vat))).performTextInput("3001")
        compose.onNodeWithText(text(R.string.history_apply_date)).performClick()
        compose.runOnIdle {
            assertEquals("Market", request.seller)
            assertEquals("3001", request.vat)
            assertEquals(StatisticsGrouping.QUARTER, request.grouping)
            assertEquals(StatisticsSort.COUNT, request.sort)
            assertTrue(request.descending)
        }
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasText(text(R.string.stats_clear_filters)))
        compose.onNodeWithText(text(R.string.stats_clear_filters)).performClick()
        compose.runOnIdle {
            assertFalse(request.filtered)
            assertEquals(StatisticsGrouping.QUARTER, request.grouping)
            assertEquals(StatisticsSort.COUNT, request.sort)
            assertTrue(request.descending)
        }
    }
}
