package com.poetscorner.invoiceqr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun ready() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text(R.string.nav_result)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun appBarsKeepControlsOutsideSystemBars() {
        ready()
        val (insets, height) = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            insets to decor.height
        }
        val title = compose.onNodeWithText(text(R.string.app_name)).fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithText(text(R.string.nav_settings)).fetchSemanticsNode().boundsInRoot
        assertTrue("Title overlaps status bar/cutout", title.top >= insets.top)
        assertTrue("Navigation overlaps system navigation", navigation.bottom <= height - insets.bottom)
    }

    @Test fun systemBarIconsFollowTheAppTheme() = runBlocking {
        ready()
        val repository = SettingsRepository(compose.activity.applicationContext)
        val original = repository.settings.first()
        try {
            for (theme in listOf("DARK", "LIGHT")) {
                repository.save(original.copy(theme = theme))
                compose.waitUntil(10_000) {
                    val window = compose.activity.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars == (theme == "LIGHT") &&
                        controller.isAppearanceLightNavigationBars == (theme == "LIGHT")
                }
            }
        } finally {
            repository.save(original)
        }
    }

    @Test fun manualEntryActionRemainsReachableAboveKeyboard() {
        ready()
        compose.onNodeWithText(text(R.string.nav_result)).performClick()
        val navigationBefore = compose.onNodeWithText(text(R.string.nav_settings))
            .fetchSemanticsNode().boundsInRoot
        compose.onNode(hasSetTextAction()).performClick().performTextInput("test")
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithText(text(R.string.manual_base64_inspect)).performScrollTo().assertIsDisplayed()
        val keyboardTop = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val keyboard = ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime())
            decor.height - keyboard.bottom
        }
        val action = compose.onNodeWithText(text(R.string.manual_base64_inspect)).fetchSemanticsNode().boundsInRoot
        assertTrue("Inspect action overlaps keyboard", action.bottom <= keyboardTop)
        val navigationAfter = compose.onNodeWithText(text(R.string.nav_settings))
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Page navigation moved with keyboard", navigationBefore.top, navigationAfter.top, 1f)
    }
}
