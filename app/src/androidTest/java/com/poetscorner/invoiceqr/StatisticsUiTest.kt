package com.poetscorner.invoiceqr

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.ui.HistoryScreen
import com.poetscorner.invoiceqr.ui.StatisticsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun typingAndDeletingKeepSuggestionsStableUntilTheNextLookupCompletes() {
        val pendingLookup = kotlinx.coroutines.CompletableDeferred<List<String>>()
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(loading = false), {}, {},
                sellerSuggestions = { if (it == "Mar") listOf("North Market") else pendingLookup.await() })
        } }
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
        val sellerField = hasSetTextAction() and hasText(text(R.string.tag_seller))
        compose.onNode(sellerField).performTextInput("Mar")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("North Market").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(sellerField).performTextInput("ket")
        compose.onNode(sellerField).assertTextContains("Market")
        compose.onNodeWithText("North Market").assertIsDisplayed()
        compose.onNode(sellerField).performTextReplacement("Mark")
        compose.onNode(sellerField).assertTextContains("Mark")
        compose.onNodeWithText("North Market").assertIsDisplayed()
        compose.onNode(sellerField).performTextReplacement("South")
        compose.onNode(sellerField).assertTextContains("South")
        compose.onNodeWithText("North Market").assertIsDisplayed()
        compose.onNode(sellerField).performTextClearance()
        compose.onNodeWithText("North Market").assertDoesNotExist()
    }

    @Test fun choosingSellerSuggestionFillsTheFieldAndWaitsForApply() {
        var request by mutableStateOf(StatisticsRequest(vat = "123"))
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {},
                sellerSuggestions = { if (it.contains("Market")) listOf("North Market") else emptyList() })
        } }
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
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
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tag_seller))).performTextInput("Market")
        compose.onNodeWithText(text(R.string.history_apply_date)).performClick()
        compose.runOnIdle { assertEquals("Market", request.seller) }
    }

    @Test fun exclusionChangesTheFieldLabelAndAcceptsAPartialNumber() {
        var request by mutableStateOf(StatisticsRequest())
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {})
        } }
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
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
        compose.onNodeWithContentDescription(text(R.string.stats_group_by)).performClick()
        compose.onNode(hasText(text(R.string.tag_seller)) and isSelected()).assertExists()
        compose.onNodeWithText(text(R.string.invoice_category)).performClick()
        compose.runOnIdle { assertEquals(StatisticsGrouping.CATEGORY, request.grouping) }
        compose.onNodeWithContentDescription(text(R.string.stats_group_by)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.invoice_category)))
        compose.onNodeWithContentDescription(text(R.string.stats_group_by)).performClick()
        compose.onNodeWithText(text(R.string.stats_half_year)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.stats_quarter)).performClick()
        compose.runOnIdle { assertEquals(StatisticsGrouping.QUARTER, request.grouping) }
        compose.onNodeWithContentDescription(text(R.string.stats_sort_by)).performClick()
        compose.onNodeWithText(text(R.string.stats_count_label)).performClick()
        compose.onNodeWithContentDescription(text(R.string.stats_sort_direction)).performClick()
        compose.onNodeWithContentDescription(text(R.string.stats_sort_direction)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.stats_descending)))
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasContentDescription(text(R.string.history_filter_title)))
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tag_seller))).performTextInput("Market")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tag_vat))).performTextInput("3001")
        compose.onNodeWithText(text(R.string.history_apply_date)).performClick()
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.stats_filters_active)))
        compose.runOnIdle {
            assertEquals("Market", request.seller)
            assertEquals("3001", request.vat)
            assertEquals(StatisticsGrouping.QUARTER, request.grouping)
            assertEquals(StatisticsSort.COUNT, request.sort)
            assertTrue(request.descending)
        }
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasText(text(R.string.stats_clear_filters)))
        compose.onNodeWithText(text(R.string.stats_clear_filters)).performClick()
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.stats_filters_inactive)))
        compose.runOnIdle {
            assertFalse(request.filtered)
            assertEquals(StatisticsGrouping.QUARTER, request.grouping)
            assertEquals(StatisticsSort.COUNT, request.sort)
            assertTrue(request.descending)
        }
    }

    @Test fun categoryCalculationCanBeChangedAndItsButtonHidesForCategoryGrouping() {
        var request by mutableStateOf(StatisticsRequest())
        compose.setContent { MaterialTheme {
            StatisticsScreen(StatisticsState(request, loading = false), { request = it }, {})
        } }
        compose.onNodeWithContentDescription(text(R.string.stats_category_calculation)).performClick()
        val categoryName = text(R.string.invoice_category_personal_purchases)
        val choice = text(R.string.stats_calculation_choice, text(R.string.invoice_category_personal_purchases),
            text(R.string.stats_calculation_subtract))
        val labelY = compose.onNodeWithText(categoryName).fetchSemanticsNode().boundsInRoot.center.y
        val choiceY = compose.onNodeWithContentDescription(choice).fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(labelY, choiceY, 2f)
        compose.onNodeWithContentDescription(choice).performClick()
        compose.runOnIdle { assertTrue(request.categoryCalculations.isEmpty()) }
        compose.onNodeWithText(text(R.string.history_apply_date)).performClick()
        compose.runOnIdle { assertEquals(StatisticsCategoryCalculation.SUBTRACT, request.calculationFor(2)) }
        compose.onNodeWithContentDescription(text(R.string.stats_group_by)).performClick()
        compose.onNodeWithText(text(R.string.invoice_category)).performClick()
        compose.onNodeWithContentDescription(text(R.string.stats_category_calculation)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.stats_total_including_tax)).assertExists()
        compose.onNodeWithText(text(R.string.stats_net_total)).assertDoesNotExist()
    }

    @Test fun groupBreakdownAppearsAboveItsInvoicesWhenCalculationIsActive() {
        val request = StatisticsRequest().withCategoryCalculation(2, StatisticsCategoryCalculation.SUBTRACT)
        val selection = StatisticsSelection(request, StatisticsKey(text = "Market"))
        val filter = HistoryFilter(statisticsSelection = selection)
        val totals = StatisticsTotals(count = 1)
        val breakdown = StatisticsGroupBreakdown(totals, totals,
            listOf(StatisticsCategoryContribution(1, StatisticsCategoryCalculation.ADD, totals, totals)))
        compose.setContent { MaterialTheme {
            HistoryScreen(HistoryPageState(filter = filter, displayedFilter = filter,
                entries = listOf(HistorySummary(1, "Market", "300", "100.00", 1L, true, 1)),
                total = 1, matching = 1, loading = false), "", {}, {}, {}, {}, {},
                groupTitle = "Market", groupSelection = selection,
                groupBreakdown = StatisticsGroupBreakdownState(selection, breakdown))
        } }
        val details = compose.onNodeWithTag("statistics_breakdown")
        val invoice = compose.onNodeWithTag("history_card_0")
        details.assertExists()
        invoice.assertExists()
        assertTrue(details.fetchSemanticsNode().boundsInRoot.bottom <= invoice.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("history_summary").assertDoesNotExist()
    }
}
