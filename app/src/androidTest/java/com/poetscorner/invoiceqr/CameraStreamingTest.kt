package com.poetscorner.invoiceqr

import android.view.View
import android.view.ViewGroup
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
import org.junit.After
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
        repository.save(ScannerSettings(scanEngine = ScanEngine.ZXING))
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
    }

    @Test fun previewStreamsAgainAfterActivityRecreation() {
        waitForStreaming()
        compose.activityRule.scenario.recreate()
        waitForStreaming()
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
