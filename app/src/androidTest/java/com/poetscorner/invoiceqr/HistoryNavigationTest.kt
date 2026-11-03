package com.poetscorner.invoiceqr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import java.util.UUID
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Before fun waitForTheActivityToRender() {
        compose.waitUntil(10000) {
            runCatching { compose.onAllNodesWithText(text(R.string.nav_history)).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    @Test fun exportsDisableForNoMatchesAndRecoverWhenSearchIsCleared() {
        compose.onNodeWithText(text(R.string.nav_history)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(UUID.randomUUID().toString())
        compose.onNodeWithContentDescription(text(R.string.export_csv)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextClearance()
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        compose.waitUntil(5000) {
            val expectedEnabled = vm.historyState.value.canExport
            val disabled = compose.onNodeWithContentDescription(text(R.string.export_csv)).fetchSemanticsNode()
                .config.contains(SemanticsProperties.Disabled)
            disabled != expectedEnabled
        }
    }

    @Test fun historyCanBeOpenedRepeatedlyAndDateDialogCanBeCancelledOrApplied() {
        repeat(3) {
            compose.onNodeWithText(text(R.string.nav_history)).performClick()
            compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
            compose.onNodeWithText(text(R.string.history_filter_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.history_scan_date)).performClick()
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
            compose.onNodeWithText(text(R.string.history_invoice_date)).performClick()
            compose.onNodeWithText(text(R.string.history_confirm_filters)).performClick()
            compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.nav_result)).performClick()
        }
    }

    @Test fun annotationSearchAndCategoryDropdownAreInsideFilters() {
        compose.onNodeWithText(text(R.string.nav_history)).performClick()
        compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.history_filter_title)).performClick()
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText(
            text(R.string.history_annotation_search))).performTextReplacement("INV-007")
        compose.onNodeWithText(text(R.string.history_all_categories)).performClick()
        compose.onNodeWithText(text(R.string.invoice_category_work)).performClick()
        compose.onNodeWithText(text(R.string.history_confirm_filters)).performClick()
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        compose.waitUntil(5000) {
            vm.historyState.value.filter.annotationQuery == "INV-007" &&
                vm.historyState.value.filter.category == 1
        }
        compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
        org.junit.Assert.assertEquals("", vm.historyState.value.filter.query)
        compose.onNode(hasSetTextAction()).performTextReplacement("Market")
        compose.waitUntil(5000) { vm.historyState.value.filter.query == "Market" }
        compose.onNodeWithText(text(R.string.history_clear_all_filters)).performClick()
        compose.waitUntil(5000) {
            val filter = vm.historyState.value.filter
            filter.query.isEmpty() && filter.annotationQuery.isEmpty() && filter.category == null &&
                filter.fromDay == null && filter.toDay == null
        }
        compose.onNodeWithText(text(R.string.history_clear_all_filters)).assertDoesNotExist()
    }
}
