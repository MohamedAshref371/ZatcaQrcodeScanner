package com.poetscorner.invoiceqr

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.SettingsRepository
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.ui.portraitLockApplies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveOrientationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var originalSettings: ScannerSettings

    @Before fun disableLockForRotationChecks() = runBlocking<Unit> {
        val repository = SettingsRepository(compose.activity.applicationContext)
        originalSettings = repository.settings.first()
        repository.save(originalSettings.copy(lockPortrait = false))
        compose.waitUntil(10_000) {
            compose.runOnIdle { compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @After fun restoreSettings() = runBlocking<Unit> {
        SettingsRepository(compose.activity.applicationContext).save(originalSettings)
    }

    @Test fun portraitPreferencePersistsAndOnlyLocksCompactFullscreen() = runBlocking<Unit> {
        val repository = SettingsRepository(compose.activity.applicationContext)
        compose.onNodeWithText(compose.activity.getString(R.string.nav_settings)).performClick()
        val label = compose.activity.getString(R.string.lock_portrait)
        val supported = compose.runOnIdle {
            portraitLockApplies(true, compose.activity.resources.configuration.smallestScreenWidthDp,
                compose.activity.isInMultiWindowMode)
        }
        if (!supported) {
            repository.save(originalSettings.copy(lockPortrait = true))
            fun assertHidden() {
                val appearance = compose.activity.getString(R.string.appearance)
                compose.onNodeWithTag("settings_list").performScrollToNode(hasText(appearance))
                compose.onNodeWithText(label).assertDoesNotExist()
                compose.runOnIdle {
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
                }
            }
            assertHidden()
            compose.activityRule.scenario.recreate()
            assertHidden()
            assertEquals(true, repository.settings.first().lockPortrait)
            return@runBlocking
        }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsOff().performClick()
        fun awaitPolicy() {
            compose.waitUntil(10_000) {
                compose.runOnIdle {
                    val activity = compose.activity
                    val expected = if (portraitLockApplies(true,
                            activity.resources.configuration.smallestScreenWidthDp, activity.isInMultiWindowMode))
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    activity.requestedOrientation == expected
                }
            }
        }
        awaitPolicy()
        assertEquals(true, repository.settings.first().lockPortrait)
        compose.activityRule.scenario.recreate()
        awaitPolicy()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsOn().performClick()
        compose.waitUntil(10_000) {
            compose.runOnIdle { compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        assertEquals(false, repository.settings.first().lockPortrait)
    }

    @Test fun launchAndRecreationDoNotRestrictOrientation() {
        fun assertUnrestricted() = compose.runOnIdle {
            val activity = compose.activity
            val info = activity.packageManager.getActivityInfo(activity.componentName, 0)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, info.screenOrientation)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        }
        compose.waitForIdle()
        assertUnrestricted()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertUnrestricted()
    }

    @Test fun settingsRemainReachableAndDoNotOverrideRotation() = runBlocking<Unit> {
        val repository = SettingsRepository(compose.activity.applicationContext)
        val original = repository.settings.first()
        val originalOrientation = compose.activity.requestedOrientation
        try {
            compose.onNodeWithText(compose.activity.getString(R.string.nav_settings)).performClick()
            for ((requested, expected) in listOf(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT
            )) {
                compose.runOnIdle { compose.activity.requestedOrientation = requested }
                compose.waitUntil(15_000) {
                    compose.runOnIdle { compose.activity.resources.configuration.orientation == expected }
                }
                // Updating app settings must leave the orientation request alone.
                repository.save(original.copy(theme = "DARK"))
                compose.waitForIdle()
                val label = compose.activity.getString(R.string.scan_crop)
                compose.onNodeWithTag("settings_list").performScrollToNode(hasText(label))
                compose.onNodeWithText(label).assertIsDisplayed()
                compose.runOnIdle { assertEquals(requested, compose.activity.requestedOrientation) }
                repository.save(original)
            }
        } finally {
            repository.save(original)
            compose.runOnIdle { compose.activity.requestedOrientation = originalOrientation }
        }
    }
}
