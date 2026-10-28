package com.poetscorner.invoiceqr

import android.view.View
import android.view.ViewGroup
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Checks the real CameraX preview and controls, beyond Compose view bounds. */
@RunWith(AndroidJUnit4::class)
class CameraStreamingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var original: ScannerSettings? = null

    @Before fun prepareCameraSettings() = runBlocking {
        val repository = SettingsRepository(compose.activity.applicationContext)
        original = repository.settings.first()
        repository.save(ScannerSettings(scanEngine = ScanEngine.ZXING, lockPortrait = false))
    }

    @After fun restoreSettings() = runBlocking {
        original?.let { SettingsRepository(compose.activity.applicationContext).save(it) }
        Unit
    }

    private fun View.findPreview(): PreviewView? = when (this) {
        is PreviewView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findPreview() }
        else -> null
    }

    private fun waitForStreaming() {
        compose.waitUntil(30_000) {
            compose.runOnIdle {
                compose.activity.window.decorView.findPreview()?.previewStreamState?.value ==
                    PreviewView.StreamState.STREAMING
            }
        }
        compose.onNodeWithTag("camera_preview").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("camera_preview").fetchSemanticsNode().boundsInRoot
        val decor = compose.activity.window.decorView
        assertEquals("Preview must reach the top edge", 0f, bounds.top, 1f)
        assertEquals("Preview must fill the screen width", decor.width.toFloat(), bounds.width, 1f)
        assertEquals("Preview must fill the screen height", decor.height.toFloat(), bounds.height, 1f)
    }

    @Test fun previewStreamsAgainAfterActivityRecreation() {
        waitForStreaming()
        compose.activityRule.scenario.recreate()
        waitForStreaming()
    }

    @Test fun rotationKeepsTheActivityPreviewAndZoomAcrossBothLandscapeDirections() {
        waitForStreaming()
        val activity = compose.activity
        val originalOrientation = activity.requestedOrientation
        val preview = compose.runOnIdle { requireNotNull(activity.window.decorView.findPreview()) }
        val provider = ProcessCameraProvider.getInstance(activity).get(10, TimeUnit.SECONDS)
        val info = CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).first()
        val zoomState = compose.runOnIdle { requireNotNull(info.zoomState.value) }
        assumeTrue("Camera must support zoom", zoomState.maxZoomRatio > zoomState.minZoomRatio)
        try {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performTouchInput {
                click(Offset(center.x * 1.5f, center.y))
            }
            compose.waitUntil(10_000) { compose.runOnIdle { (info.zoomState.value?.linearZoom ?: 0f) > 0.1f } }
            val expectedZoom = compose.runOnIdle { requireNotNull(info.zoomState.value).zoomRatio }
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                val previousRotation = compose.runOnIdle { preview.display.rotation }
                compose.runOnIdle { activity.requestedOrientation = orientation }
                compose.waitUntil(15_000) {
                    compose.runOnIdle {
                        val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                            Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                        activity.resources.configuration.orientation == expected && preview.display.rotation != previousRotation
                    }
                }
                waitForStreaming()
                compose.runOnIdle {
                    assertSame("Rotation must not recreate the Activity", activity, compose.activity)
                    assertSame("Rotation must retain the PreviewView", preview, activity.window.decorView.findPreview())
                    assertEquals("Rotation must preserve camera zoom", expectedZoom, requireNotNull(info.zoomState.value).zoomRatio, 0.01f)
                }
            }
        } finally {
            compose.runOnIdle { activity.requestedOrientation = originalOrientation }
        }
    }

    @Test fun cropSwitchPersistsAndControlsTheCameraGuide() = runBlocking<Unit> {
        val repository = SettingsRepository(compose.activity.applicationContext)
        fun text(id: Int) = compose.activity.getString(id)
        waitForStreaming()
        compose.onNodeWithTag("camera_crop_guide").assertIsDisplayed()

        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.scan_crop)))
        compose.onNodeWithText(text(R.string.scan_crop)).assertIsOn().performClick()
        withTimeout(10000) { repository.settings.first { !it.cropEnabled } }
        compose.onNodeWithText(text(R.string.nav_scanner)).performClick()
        waitForStreaming()
        compose.onNodeWithTag("camera_crop_guide").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.camera_hint_full_frame)).assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        waitForStreaming()
        assertFalse(SettingsRepository(compose.activity.applicationContext).settings.first().cropEnabled)
        compose.onNodeWithTag("camera_crop_guide").assertDoesNotExist()

        compose.onNodeWithText(text(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(text(R.string.scan_crop)))
        compose.onNodeWithText(text(R.string.scan_crop)).assertIsOff().performClick()
        withTimeout(10000) { repository.settings.first { it.cropEnabled } }
        compose.onNodeWithText(text(R.string.nav_scanner)).performClick()
        waitForStreaming()
        compose.onNodeWithTag("camera_crop_guide").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.camera_hint)).assertIsDisplayed()
    }

    @Test fun zoomControlChangesCameraStateAndPreviewKeepsStreaming() {
        waitForStreaming()
        val provider = ProcessCameraProvider.getInstance(compose.activity).get(10, TimeUnit.SECONDS)
        val info = CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).first()
        val zoom = compose.runOnIdle { requireNotNull(info.zoomState.value) }
        assumeTrue("Camera must support zoom", zoom.maxZoomRatio > zoom.minZoomRatio)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performTouchInput {
            click(Offset(center.x * 1.5f, center.y))
        }
        compose.waitUntil(10_000) {
            compose.runOnIdle { (info.zoomState.value?.linearZoom ?: 0f) > 0.1f }
        }
        compose.onNodeWithTag("camera_preview").performTouchInput { click(center) }
        waitForStreaming()
    }
}
