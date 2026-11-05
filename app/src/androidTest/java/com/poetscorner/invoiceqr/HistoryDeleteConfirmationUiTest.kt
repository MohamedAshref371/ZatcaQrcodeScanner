package com.poetscorner.invoiceqr

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryDeletionPeriod
import com.poetscorner.invoiceqr.data.HistoryDeletionPlan
import com.poetscorner.invoiceqr.ui.HistoryDeleteConfirmationDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDeleteConfirmationUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun zeroMatchesShowsInformationAndOnlyDismissAction() {
        var dismissed = false
        var confirmed = false
        val plan = HistoryDeletionPlan.create(HistoryDateSource.SCAN, HistoryDeletionPeriod.TWO_YEARS)
        compose.setContent { MaterialTheme {
            HistoryDeleteConfirmationDialog(plan, { 0 },
                onDismiss = { dismissed = true }, onConfirm = { confirmed = true })
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.history_delete_empty_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.history_delete_empty_period,
            text(R.string.history_delete_two_years), text(R.string.history_scan_date))).assertExists()
        compose.onNodeWithText(text(R.string.history_delete_confirm_action)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.cancel)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.history_delete_empty_action)).performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(confirmed) }
    }

    @Test fun zeroMatchesForDeleteAllShowsEmptyHistory() {
        val plan = HistoryDeletionPlan.create(HistoryDateSource.INVOICE, HistoryDeletionPeriod.ALL)
        compose.setContent { MaterialTheme {
            HistoryDeleteConfirmationDialog(plan, { 0 }, onDismiss = {}, onConfirm = {})
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.history_delete_empty_all))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.history_delete_confirm_action)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.history_delete_empty_action)).assertExists()
    }

    @Test fun positiveMatchesStillRequireConfirmation() {
        var confirmed = false
        val plan = HistoryDeletionPlan.create(HistoryDateSource.SCAN, HistoryDeletionPeriod.THREE_DAYS)
        compose.setContent { MaterialTheme {
            HistoryDeleteConfirmationDialog(plan, { 2 }, onDismiss = {},
                onConfirm = { confirmed = true })
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.history_delete_count, 2))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.history_delete_empty_action)).assertDoesNotExist()
        compose.onNode(hasText(text(R.string.history_delete_confirm_action)) and hasClickAction())
            .performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
