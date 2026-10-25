package com.poetscorner.invoiceqr

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TitleBarScrollTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)
    private fun titleHeight() = compose.onNodeWithTag("app_title_bar").fetchSemanticsNode().boundsInRoot.height
    private fun navigationTop() = compose.onNodeWithText(text(R.string.nav_settings))
        .fetchSemanticsNode().boundsInRoot.top
    private fun settingsPosition() = compose.onNodeWithTag("settings_list").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun openSettings() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text(R.string.nav_settings)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").assertIsDisplayed()
    }

    @Test fun titleHidesAndReturnsOnReverseScrollWhileNavigationStaysFixed() {
        openSettings()
        val expanded = titleHeight()
        val navigation = navigationTop()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        assertTrue("Title should collapse completely", titleHeight() < expanded / 2)
        assertEquals(navigation, navigationTop(), 1f)

        // A short reverse gesture, well before returning to the start of the list.
        compose.onNodeWithTag("settings_list").performTouchInput {
            swipe(Offset(centerX, height * 0.3f), Offset(centerX, height * 0.6f), 600)
        }
        assertEquals("Title should return as soon as scrolling reverses", expanded, titleHeight(), 1f)
        compose.onNodeWithText(text(R.string.settings_heading)).assertIsNotDisplayed()
        assertEquals(navigation, navigationTop(), 1f)
    }

    @Test fun titleIsSharedAcrossTabsAndCameraRevealsItForEveryTab() {
        openSettings()
        val expanded = titleHeight()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        assertTrue(titleHeight() < expanded / 2)
        val collapsed = titleHeight()
        val position = settingsPosition()
        for (tab in listOf(R.string.nav_history, R.string.nav_statistics, R.string.nav_result)) {
            compose.onNodeWithText(text(tab)).performClick()
            assertEquals("All content tabs share the hidden title", collapsed, titleHeight(), 1f)
        }
        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        assertEquals(collapsed, titleHeight(), 1f)
        assertEquals("Returning preserves settings scroll", position, settingsPosition(), 0.01f)
        compose.onNodeWithTag("settings_list").performTouchInput { swipeDown() }
        assertEquals(expanded, titleHeight(), 1f)
        compose.onNodeWithText(text(R.string.nav_history)).performClick()
        assertEquals("A revealed title is shared too", expanded, titleHeight(), 1f)
        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        assertTrue(titleHeight() < expanded / 2)
        compose.onNodeWithText(text(R.string.nav_scanner)).performClick()
        assertEquals("Entering camera reveals the shared title", expanded, titleHeight(), 1f)
        compose.onRoot().performTouchInput { swipeUp() }
        assertEquals("Camera title stays fixed", expanded, titleHeight(), 1f)
        for (tab in listOf(R.string.nav_settings, R.string.nav_history, R.string.nav_statistics, R.string.nav_result)) {
            compose.onNodeWithText(text(tab)).performClick()
            assertEquals("Leaving camera keeps the title visible in every tab", expanded, titleHeight(), 1f)
        }
    }

    @Test fun tabContentAndTitleAreStableFromTheFirstFrame() {
        openSettings()
        val expanded = titleHeight()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        val collapsed = titleHeight()
        val position = settingsPosition()
        val listTop = compose.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot.top
        assertTrue(position > 0f)
        try {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(text(R.string.nav_history)).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Shared hidden title is used on the first frame", collapsed, titleHeight(), 1f)
            compose.mainClock.advanceTimeBy(600)
            assertEquals(collapsed, titleHeight(), 1f)

            compose.onNodeWithText(text(R.string.nav_settings)).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Settings title is restored on its first frame", collapsed, titleHeight(), 1f)
            assertEquals(position, settingsPosition(), 0.01f)
            assertEquals(listTop, compose.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot.top, 1f)
            compose.mainClock.advanceTimeBy(600)
            assertEquals("Title must not adjust after opening", collapsed, titleHeight(), 1f)
            assertEquals(position, settingsPosition(), 0.01f)
            assertEquals(listTop, compose.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot.top, 1f)
            compose.onNodeWithText(text(R.string.nav_scanner)).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Camera reveals the shared title on its first frame", expanded, titleHeight(), 1f)
            compose.onNodeWithText(text(R.string.nav_settings)).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("Other pages keep the camera's visible title immediately", expanded, titleHeight(), 1f)
            compose.mainClock.advanceTimeBy(600)
            assertEquals(expanded, titleHeight(), 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
