package com.poetscorner.zatcaqrcodescanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import com.poetscorner.zatcaqrcodescanner.ui.ScannerViewModel
import java.util.UUID
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun exportsDisableForNoMatchesAndRecoverWhenSearchIsCleared() {
        compose.onNodeWithText(text(R.string.nav_history)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(UUID.randomUUID().toString())
        compose.onNodeWithText(text(R.string.export_json)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.export_csv)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextClearance()
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        compose.waitUntil(5000) {
            val expectedEnabled = vm.historyEntries.value.isNotEmpty()
            listOf(R.string.export_json, R.string.export_csv).all { id ->
                val disabled = compose.onNodeWithText(text(id)).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
                disabled != expectedEnabled
            }
        }
    }

    @Test fun historyCanBeOpenedRepeatedlyAndDateDialogCanBeCancelledOrApplied() {
        repeat(3) {
            compose.onNodeWithText(text(R.string.nav_history)).performClick()
            compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.history_filter_search)).performClick()
            compose.onNodeWithText(text(R.string.history_filter_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.history_scan_date)).performClick()
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.history_filter_search)).performClick()
            compose.onNodeWithText(text(R.string.history_invoice_date)).performClick()
            compose.onNodeWithText(text(R.string.history_confirm_filters)).performClick()
            compose.onNodeWithText(text(R.string.history_search)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.nav_result)).performClick()
        }
    }
}
