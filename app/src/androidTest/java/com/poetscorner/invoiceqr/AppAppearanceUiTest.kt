package com.poetscorner.invoiceqr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppAppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private fun settingsItem(id: Int) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(id)))
    }

    @Test fun colorsAreAvailableWithoutPurchaseAndSurviveRecreation() {
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        settingsItem(R.string.app_appearance)
        compose.onNodeWithText(text(R.string.app_appearance)).performClick()
        compose.onNodeWithText(text(R.string.blue_theme)).performClick()
        compose.waitUntil(5_000) { vm.settings.value.appTheme == "BLUE" }
        compose.activityRule.scenario.recreate()
        settingsItem(R.string.blue_theme)
        compose.onNodeWithText(text(R.string.blue_theme)).assertIsDisplayed()
        assertEquals("BLUE", vm.settings.value.appTheme)

        settingsItem(R.string.about_app)
        compose.onNodeWithText(text(R.string.about_app)).performClick()
        compose.onNodeWithText(text(R.string.about_details)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).performClick()
    }
}
