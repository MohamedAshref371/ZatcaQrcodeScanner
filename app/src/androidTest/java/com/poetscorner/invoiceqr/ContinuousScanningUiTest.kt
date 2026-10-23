package com.poetscorner.invoiceqr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuousScanningUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun floatingResultDoesNotMoveCameraAndOnlyClosingTheActivityResetsBatchMode() = runBlocking<Unit> {
        compose.waitUntil(10000) {
            runCatching { compose.onAllNodesWithTag("continuous_scan_toggle").fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        val invoice = historyFixture(901, seller = "Continuous UI seller", hash = false)
        val db = Room.databaseBuilder(compose.activity.applicationContext, ScanDatabase::class.java, "invoices.db").build()
        try {
            val cameraBounds = compose.onNodeWithTag("camera_preview").fetchSemanticsNode().boundsInRoot
            val toggleBounds = compose.onNodeWithTag("continuous_scan_toggle").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("continuous_scan_toggle").performClick()
            compose.runOnIdle { assertTrue(vm.continuousScan.value.enabled) }
            assertEquals(toggleBounds, compose.onNodeWithTag("continuous_scan_toggle").fetchSemanticsNode().boundsInRoot)
            assertEquals(cameraBounds, compose.onNodeWithTag("camera_preview").fetchSemanticsNode().boundsInRoot)
            withContext(Dispatchers.Main) { assertTrue(vm.scanned(invoice.raw)) }
            compose.onNodeWithTag("continuous_scan_notice").assertDoesNotExist()
            compose.onNodeWithTag("continuous_scan_count", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("continuous_scan_toggle").assertIsDisplayed()
            compose.runOnIdle { assertNull(vm.result.value) }
            val viewResult = compose.activity.getString(R.string.continuous_scan_view_result)
            compose.onNodeWithText(viewResult).assertIsDisplayed()
            assertEquals(cameraBounds, compose.onNodeWithTag("camera_preview").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithText(viewResult).performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("continuous_scan_toggle").fetchSemanticsNodes().isEmpty()
            }
            compose.runOnIdle { assertEquals(invoice.raw, vm.result.value?.invoice?.raw) }
            compose.onNodeWithText(compose.activity.getString(R.string.nav_scanner)).performClick()
            compose.onNodeWithTag("continuous_scan_toggle").assertIsDisplayed()
            compose.runOnIdle { assertTrue(vm.continuousScan.value.enabled) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("continuous_scan_toggle").fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { assertTrue(vm.continuousScan.value.enabled) }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle {
                assertTrue(vm.continuousScan.value.enabled)
                assertEquals(1, vm.continuousScan.value.added)
            }
            compose.onNodeWithTag("continuous_scan_notice").assertDoesNotExist()
            compose.activityRule.scenario.close()
            ActivityScenario.launch(MainActivity::class.java).use { fresh ->
                fresh.onActivity { activity ->
                    val freshVm = ViewModelProvider(activity)[ScannerViewModel::class.java]
                    assertFalse(freshVm.continuousScan.value.enabled)
                    assertEquals(0, freshVm.continuousScan.value.added)
                }
            }
        } finally {
            db.scans().byPayloadKey(invoice.payloadKey)?.let { db.scans().delete(it.id) }
            db.close()
        }
    }
}
