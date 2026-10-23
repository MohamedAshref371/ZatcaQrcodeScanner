package com.poetscorner.invoiceqr

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.billing.SupporterViewModel
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupporterPreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private fun settingsItem(id: Int) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(id)))
    }

    @Test fun previewWorksAcrossScreensAndRecreationWithoutGrantingOwnershipOrChangingPreferences() {
        val context = compose.activity.applicationContext
        val entitlement = context.getSharedPreferences("supporter_entitlement", Context.MODE_PRIVATE)
        val entitlementBefore = entitlement.all.toMap()
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        val supporter = compose.runOnIdle { ViewModelProvider(compose.activity)[SupporterViewModel::class.java] }
        assertFalse(supporter.state.value.owned)
        val goldenBefore = vm.settings.value.goldenTheme

        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        settingsItem(R.string.supporter_preview_start)
        compose.onNodeWithTag("supporter_preview_toggle").assertIsEnabled().performClick()
        compose.onNodeWithTag("supporter_preview_banner").assertDoesNotExist()
        compose.onNodeWithTag("supporter_preview_toggle").assertTextContains(text(R.string.supporter_preview_end))
        settingsItem(R.string.gold_theme)
        compose.onNodeWithText(text(R.string.gold_theme)).assertIsOn().performClick().assertIsOff()

        for (screen in listOf(R.string.nav_history, R.string.nav_result, R.string.nav_scanner, R.string.nav_settings)) {
            compose.onNodeWithText(text(screen)).performClick()
            compose.onNodeWithTag("supporter_preview_banner").assertDoesNotExist()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("supporter_preview_banner").assertDoesNotExist()
        settingsItem(R.string.supporter_preview_end)
        compose.onNodeWithTag("supporter_preview_toggle").assertTextContains(text(R.string.supporter_preview_end))
        settingsItem(R.string.gold_theme)
        compose.onNodeWithText(text(R.string.gold_theme)).assertIsOff()

        settingsItem(R.string.about_app)
        compose.onNodeWithText(text(R.string.about_app)).performClick()
        compose.onNodeWithText(text(R.string.supporter_preview_badge)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.supporter_badge)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).performClick()

        settingsItem(R.string.supporter_preview_end)
        compose.onNodeWithTag("supporter_preview_toggle").performClick()
        compose.onNodeWithTag("supporter_preview_banner").assertDoesNotExist()
        settingsItem(R.string.supporter_preview_start)
        compose.onNodeWithText(text(R.string.gold_theme)).assertDoesNotExist()
        compose.onNodeWithTag("supporter_preview_toggle").performClick()
        settingsItem(R.string.gold_theme)
        compose.onNodeWithText(text(R.string.gold_theme)).assertIsOn()
        settingsItem(R.string.supporter_preview_end)
        compose.onNodeWithTag("supporter_preview_toggle").performClick()

        assertFalse(supporter.state.value.owned)
        assertEquals(entitlementBefore, entitlement.all)
        assertEquals(goldenBefore, vm.settings.value.goldenTheme)
    }
}
