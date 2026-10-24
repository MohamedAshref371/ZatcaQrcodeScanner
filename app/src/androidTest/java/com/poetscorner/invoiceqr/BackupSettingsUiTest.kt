package com.poetscorner.invoiceqr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun backupAppearsAboveAppearanceAndCancellingEitherPickerReenablesActions() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(text(R.string.nav_settings)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.settings_backup)))
        val backupTop = compose.onNodeWithText(text(R.string.settings_backup)).fetchSemanticsNode().boundsInRoot.top
        val appearanceTop = compose.onNodeWithText(text(R.string.settings_app)).fetchSemanticsNode().boundsInRoot.top
        assertTrue(backupTop < appearanceTop)
        for (action in listOf(R.string.backup_create, R.string.backup_import)) {
            compose.onNodeWithText(text(action)).assertIsEnabled().performClick()
            compose.waitUntil(10000) {
                !compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            // The system picker belongs to another process, so Espresso cannot target it.
            AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
            compose.waitUntil(10000) {
                compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            compose.onNodeWithText(text(R.string.backup_create)).assertIsEnabled()
            compose.onNodeWithText(text(R.string.backup_import)).assertIsEnabled()
        }
    }
}
