package com.poetscorner.invoiceqr

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
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

    @Test fun choosingSellerSuggestionFillsTheFieldAndWaitsForApply() {
        var request by mutableStateOf(StatisticsRequest(vat = "123"))
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {},
                sellerSuggestions = { if (it.contains("Market")) listOf("North Market") else emptyList() })
        } }
        compose.onNodeWithText(text(R.string.history_filter_title)).performClick()
        val sellerField = hasSetTextAction() and hasText(text(R.string.tag_seller))
        compose.onNode(sellerField).performTextInput("Market")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("North Market").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("North Market").performClick()
        compose.onNode(sellerField).assertTextContains("North Market")
        compose.onNode(sellerField).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.TextSelectionRange, TextRange("North Market".length)))
        compose.runOnIdle { assertEquals("", request.seller) }
        compose.onNodeWithText(text(R.string.history_apply_date)).performClick()
        compose.runOnIdle {
            assertEquals("North Market", request.seller)
            assertEquals("123", request.vat)
        }
    }

    @Test fun sellerCanStillApplyAPartialNameWithoutChoosingASuggestion() {
        var request by mutableStateOf(StatisticsRequest())
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {},
                sellerSuggestions = { emptyList() })
        } }
        compose.onNodeWithText(text(R.string.history_filter_title)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tag_seller))).performTextInput("Market")
        compose.onNodeWithText(text(R.string.history_apply_date)).performClick()
        compose.runOnIdle { assertEquals("Market", request.seller) }
    }

    @Test fun exclusionChangesTheFieldLabelAndAcceptsAPartialNumber() {
        var request by mutableStateOf(StatisticsRequest())
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {})
        } }
        compose.onNodeWithText(text(R.string.history_filter_title)).performClick()
        compose.onNodeWithText(text(R.string.stats_vat_exclude)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.stats_exclude_vat))).performTextInput("123")
        compose.onNodeWithText(text(R.string.history_apply_date)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("123", request.excludedVat)
            assertEquals("", request.vat)
            assertEquals(StatisticsVatMode.EXCLUDE, request.vatMode)
        }
    }

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
