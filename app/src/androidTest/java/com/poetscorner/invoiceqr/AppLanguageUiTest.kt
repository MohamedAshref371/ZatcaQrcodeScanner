package com.poetscorner.invoiceqr

import android.view.View
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun showPicker() {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.app_language)))
        compose.onNodeWithText(text(R.string.app_language)).performClick()
    }

    private fun waitForSettings() {
        compose.waitUntil(10000) {
            runCatching { compose.onAllNodesWithTag("settings_list").fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    private fun waitForNavigation() {
        compose.waitUntil(15000) {
            runCatching {
                compose.onAllNodesWithText(text(R.string.nav_settings)).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun assertNavigationDirection(direction: Int) {
        val scannerX = compose.onNodeWithText(text(R.string.nav_scanner)).fetchSemanticsNode().boundsInRoot.center.x
        val settingsX = compose.onNodeWithText(text(R.string.nav_settings)).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Navigation must visually follow the selected language (scanner=$scannerX, settings=$settingsX)",
            if (direction == View.LAYOUT_DIRECTION_RTL) scannerX > settingsX else scannerX < settingsX)
    }

    @Test fun matchingDeviceLanguageChangesPreferenceWithoutReplacingTheScreen() {
        var original = LocaleListCompat.getEmptyLocaleList()
        compose.runOnIdle { original = AppCompatDelegate.getApplicationLocales() }
        val deviceLanguage = Resources.getSystem().configuration.locales[0].language
        assumeTrue(deviceLanguage in listOf("ar", "en"))
        try {
            compose.runOnIdle { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
            waitForNavigation()
            compose.onNodeWithText(text(R.string.nav_settings)).performClick()
            val activity = compose.activity
            val decor = activity.window.decorView
            val label = if (deviceLanguage == "ar") R.string.language_arabic else R.string.language_english
            repeat(2) {
                showPicker()
                compose.onAllNodesWithText(text(label)).onLast().performClick()
                compose.waitForIdle()
                assertSame(activity, compose.activity)
                assertSame(decor, compose.activity.window.decorView)
                compose.runOnIdle {
                    assertEquals(deviceLanguage, AppCompatDelegate.getApplicationLocales()[0]?.language)
                }
                showPicker()
                compose.onAllNodesWithText(text(R.string.language_system)).onLast().performClick()
                compose.waitForIdle()
                assertSame(activity, compose.activity)
                assertSame(decor, compose.activity.window.decorView)
                compose.runOnIdle { assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty) }
                compose.onNodeWithTag("settings_list").assertExists()
                compose.onNode(hasText(text(R.string.app_language)) and hasText(text(R.string.language_system)))
                    .assertIsDisplayed()
                assertEquals(deviceLanguage, compose.activity.resources.configuration.locales[0].language)
                assertNavigationDirection(if (deviceLanguage == "ar")
                    View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
            }
        } finally {
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
        }
    }

    @Test fun languageSwitchesDirectionKeepsSettingsAndSurvivesRecreation() {
        var original = LocaleListCompat.getEmptyLocaleList()
        compose.runOnIdle { original = AppCompatDelegate.getApplicationLocales() }
        try {
            waitForNavigation()
            compose.onNodeWithText(text(R.string.nav_settings)).performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.settings_app)))
            val titleTop = compose.onNodeWithText(text(R.string.settings_app)).fetchSemanticsNode().boundsInRoot.top
            val languageTop = compose.onNodeWithText(text(R.string.app_language)).fetchSemanticsNode().boundsInRoot.top
            val appearanceTop = compose.onNodeWithText(text(R.string.appearance)).fetchSemanticsNode().boundsInRoot.top
            assertTrue(titleTop < languageTop && languageTop < appearanceTop)

            for ((language, label, direction) in listOf(
                Triple("ar", R.string.language_arabic, View.LAYOUT_DIRECTION_RTL),
                Triple("en", R.string.language_english, View.LAYOUT_DIRECTION_LTR),
                Triple("ar", R.string.language_arabic, View.LAYOUT_DIRECTION_RTL),
                Triple("en", R.string.language_english, View.LAYOUT_DIRECTION_LTR)
            )) {
                val activity = compose.activity
                val decor = activity.window.decorView
                showPicker()
                compose.onAllNodesWithText(text(label)).onLast().performClick()
                compose.waitUntil(10000) {
                    compose.activity.resources.configuration.locales[0].language == language
                }
                compose.onNodeWithTag("settings_list").assertExists()
                assertSame("Language changes must keep the existing screen", activity, compose.activity)
                assertSame(decor, compose.activity.window.decorView)
                assertNavigationDirection(direction)
                compose.activityRule.scenario.onActivity {
                    assertEquals(language, AppCompatDelegate.getApplicationLocales()[0]?.language)
                    assertEquals(direction, it.resources.configuration.layoutDirection)
                    // Export labels must also follow the app language on Android 8-12.
                    assertEquals(it.getString(R.string.tag_seller),
                        ContextCompat.getString(it.applicationContext, R.string.tag_seller))
                }
            }
            compose.activityRule.scenario.recreate()
            waitForSettings()
            assertEquals("en", compose.activity.resources.configuration.locales[0].language)
            assertNavigationDirection(View.LAYOUT_DIRECTION_LTR)
            showPicker()
            compose.onAllNodesWithText(text(R.string.language_system)).onLast().performClick()
            compose.waitForIdle()
            compose.activityRule.scenario.onActivity {
                assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
            }
            compose.onNodeWithTag("settings_list").assertExists()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.app_language)))
            compose.onNode(hasText(text(R.string.app_language)) and hasText(text(R.string.language_system)))
                .assertIsDisplayed()
            assertNavigationDirection(compose.activity.resources.configuration.layoutDirection)
        } finally {
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
        }
    }
}
