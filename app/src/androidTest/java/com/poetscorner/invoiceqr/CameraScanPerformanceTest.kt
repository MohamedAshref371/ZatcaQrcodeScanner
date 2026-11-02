package com.poetscorner.invoiceqr

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.SettingsRepository
import com.poetscorner.invoiceqr.ui.CameraFrameTiming
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real camera benchmark. Run on a physical device with camera permission granted. */
@RunWith(AndroidJUnit4::class)
class CameraScanPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun measureHdZxingWithAndWithoutCrop() = runBlocking {
        val settings = SettingsRepository(compose.activity.applicationContext)
        val original = settings.settings.first()
        CameraFrameTiming.enabled = true
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("continuous_scan_toggle").fetchSemanticsNodes().isNotEmpty()
            }
            // Keep the camera open if a QR happens to enter the field of view.
            compose.onNodeWithTag("continuous_scan_toggle").performClick()
            for (cropEnabled in listOf(true, false)) {
                CameraFrameTiming.clear()
                settings.save(ScannerSettings(scanEngine = ScanEngine.ZXING, resolution = "HD",
                    speed = "FASTEST", cropEnabled = cropEnabled))
                compose.waitUntil(30_000) {
                    CameraFrameTiming.snapshot().count { it.cropEnabled == cropEnabled } >= 3
                }
                CameraFrameTiming.clear() // Discard camera startup and settings transition.
                SystemClock.sleep(10_000)
                val samples = CameraFrameTiming.snapshot().filter { it.cropEnabled == cropEnabled }
                assertTrue("Too few camera frames for a meaningful measurement: ${samples.size}", samples.size >= 30)
                val width = samples.first().width
                val height = samples.first().height
                val message = "crop=$cropEnabled frames=${samples.size} resolution=${width}x${height} " +
                    "main+crop=${summary(samples.map { it.mainAndCropNs })} " +
                    "decode=${summary(samples.map { it.decodeNs })} " +
                    "total=${summary(samples.map { it.mainAndCropNs + it.decodeNs })} " +
                    "qrFound=${samples.count { it.foundQr }}"
                Log.i("CameraScanPerformance", message)
                println("CameraScanPerformance $message")
            }
        } finally {
            CameraFrameTiming.enabled = false
            CameraFrameTiming.clear()
            settings.save(original)
        }
    }

    private fun summary(values: List<Long>): String {
        val sorted = values.sorted()
        fun percentile(p: Double) = "%.1f".format(java.util.Locale.US,
            sorted[((sorted.size - 1) * p).toInt()] / 1_000_000.0)
        return "p50=${percentile(0.50)}ms,p95=${percentile(0.95)}ms,max=${percentile(1.0)}ms"
    }
}
