package com.poetscorner.invoiceqr

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PatternMatcher
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun policyRequiresConfirmationAndUsesCanonicalUrlAfterRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uri = Uri.parse(BuildConfig.PRIVACY_POLICY_URL)
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme(uri.scheme)
            addDataAuthority(uri.host, null)
            addDataPath(uri.path, PatternMatcher.PATTERN_LITERAL)
        }
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            compose.waitUntil(10000) {
                compose.onAllNodesWithText(text(R.string.nav_settings)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(text(R.string.nav_settings)).performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.privacy_policy)))
            compose.onNodeWithText(text(R.string.privacy_policy)).performClick()
            compose.onNodeWithText(text(R.string.privacy_open_message)).assertIsDisplayed()
            assertEquals(0, monitor.hits)
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            compose.onNodeWithText(text(R.string.privacy_open_message)).assertDoesNotExist()
            assertEquals(0, monitor.hits)
            compose.onNodeWithText(text(R.string.privacy_policy)).performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(text(R.string.privacy_open_message)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.privacy_open_link)).performClick()
            compose.waitForIdle()
            assertEquals(1, monitor.hits)
            compose.onNodeWithText(text(R.string.privacy_open_message)).assertDoesNotExist()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
