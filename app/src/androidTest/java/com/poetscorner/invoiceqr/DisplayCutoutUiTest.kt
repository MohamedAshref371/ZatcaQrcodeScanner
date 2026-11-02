package com.poetscorner.invoiceqr

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayCutoutUiTest {
    private fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) {
            findText(node.getChild(index), text)?.let { return it }
        }
        return null
    }

    private fun awaitBounds(text: String): Rect {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            findText(automation.rootInActiveWindow, text)?.let { node ->
                return Rect().also(node::getBoundsInScreen)
            }
            SystemClock.sleep(100)
        }
        fail("Text not visible: $text")
        error("unreachable")
    }

    @Test fun scannerAndHistoryStayOutsideSideCutout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var labels: List<String>
            scenario.onActivity { activity ->
                labels = listOf(activity.getString(R.string.app_name),
                    activity.getString(R.string.scanner_heading),
                    activity.getString(R.string.continuous_scan),
                    activity.getString(R.string.nav_settings),
                    activity.getString(R.string.history_heading))
            }
            awaitBounds(labels[0])
            var left = 0
            var right = 0
            var width = 0
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val cutout = ViewCompat.getRootWindowInsets(decor)!!
                    .getInsets(WindowInsetsCompat.Type.displayCutout())
                left = cutout.left
                right = cutout.right
                width = decor.width
            }
            assumeTrue("Run in landscape with a side display cutout", left > 0 || right > 0)
            fun assertSafe(text: String) {
                val bounds = awaitBounds(text)
                assertTrue("$text overlaps left cutout: $bounds", bounds.left >= left)
                assertTrue("$text overlaps right cutout: $bounds", bounds.right <= width - right)
            }
            labels.take(4).forEach(::assertSafe)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[com.poetscorner.invoiceqr.ui.ScannerViewModel::class.java].selectTab(2)
            }
            assertSafe(labels[4])
        }
    }
}
