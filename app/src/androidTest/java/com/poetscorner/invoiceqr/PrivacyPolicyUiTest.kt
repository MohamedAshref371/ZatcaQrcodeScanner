package com.poetscorner.invoiceqr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun policyIsReadableFromSettingsAndSurvivesActivityRecreation() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(text(R.string.nav_settings)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.privacy_policy)))
        compose.onNodeWithText(text(R.string.privacy_policy)).performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("vStudio", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("privacy_policy_content").performScrollToNode(hasText("afi4test2@gmail.com", substring = true))
        compose.onNodeWithText("afi4test2@gmail.com", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("privacy_policy_content").performScrollToNode(hasText("Google ML Kit", substring = true))
        compose.onNodeWithText(text(R.string.privacy_load_error)).assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("privacy_policy_content").assertIsDisplayed()
        compose.onNodeWithTag("privacy_policy_content").performScrollToNode(hasText("afi4test2@gmail.com", substring = true))
        compose.onNodeWithText("afi4test2@gmail.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithTag("privacy_policy_content").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.privacy_policy)).assertIsDisplayed()
    }
}
