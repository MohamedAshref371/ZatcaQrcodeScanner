package com.poetscorner.zatcaqrcodescanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

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
