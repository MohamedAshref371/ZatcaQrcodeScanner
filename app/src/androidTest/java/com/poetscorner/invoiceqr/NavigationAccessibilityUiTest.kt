package com.poetscorner.invoiceqr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationAccessibilityUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** Run with system font_scale >= 1.5; the normal-size navigation keeps visible text labels. */
    @Test fun iconOnlyNavigationTabsHaveActionableNames() {
        assumeTrue(compose.activity.resources.configuration.fontScale >= 1.5f)
        compose.waitForIdle()
        for (label in listOf(R.string.nav_result, R.string.nav_history,
                R.string.nav_statistics, R.string.nav_settings)) {
            compose.onNode(hasContentDescription(compose.activity.getString(label)) and hasClickAction())
                .assertExists()
        }
    }
}
