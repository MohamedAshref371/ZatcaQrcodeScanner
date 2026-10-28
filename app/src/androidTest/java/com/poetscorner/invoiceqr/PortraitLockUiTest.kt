package com.poetscorner.invoiceqr

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortraitLockUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun portraitLockDefaultsOnAndBothChoicesPersistAcrossRecreation() = runBlocking<Unit> {
        val repository = SettingsRepository(compose.activity.applicationContext)
        val original = repository.settings.first()
        assertTrue(ScannerSettings().lockPortrait)
        try {
            repository.save(original.copy(lockPortrait = true))
            fun awaitOrientation(locked: Boolean) {
                compose.waitUntil(10_000) {
                    compose.runOnIdle {
                        compose.activity.requestedOrientation == if (locked)
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_USER
                    }
                }
                if (locked) compose.runOnIdle {
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, compose.activity.resources.configuration.orientation)
                }
            }
            awaitOrientation(true)
            compose.onNodeWithText(compose.activity.getString(R.string.nav_settings)).performClick()
            fun toggle() {
                val label = compose.activity.getString(R.string.lock_portrait)
                compose.onNodeWithTag("settings_list").performScrollToNode(hasText(label))
                compose.onNodeWithText(label).performClick()
            }
            val label = compose.activity.getString(R.string.lock_portrait)
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsOn()
            toggle()
            withTimeout(10_000) { repository.settings.first { !it.lockPortrait } }
            awaitOrientation(false)
            compose.activityRule.scenario.recreate()
            awaitOrientation(false)
            compose.onNodeWithText(compose.activity.getString(R.string.lock_portrait)).assertIsOff()
            toggle()
            withTimeout(10_000) { repository.settings.first { it.lockPortrait } }
            awaitOrientation(true)
            compose.activityRule.scenario.recreate()
            awaitOrientation(true)
            compose.onNodeWithText(compose.activity.getString(R.string.lock_portrait)).assertIsOn()
        } finally {
            repository.save(original)
        }
    }
}
