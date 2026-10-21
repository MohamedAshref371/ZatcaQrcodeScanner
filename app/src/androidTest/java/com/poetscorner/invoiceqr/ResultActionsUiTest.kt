package com.poetscorner.invoiceqr

import android.content.ClipboardManager
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultActionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun sharesFromTheHeaderAndCopiesOnlyTheVatNumber() = runBlocking<Unit> {
        val context = compose.activity.applicationContext
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        val fixture = historyFixture(102001, seller = "Result actions ${System.nanoTime()}", hash = false)
        var id: Long? = null
        try {
            withContext(Dispatchers.Main) {
                assertTrue(vm.scanned(fixture.raw))
                id = vm.result.value!!.scanId
            }
            val shareLabel = compose.activity.getString(R.string.share_invoice)
            val copyLabel = compose.activity.getString(R.string.copy_vat)
            val header = compose.onNodeWithText(compose.activity.getString(R.string.result_passed))
                .fetchSemanticsNode().boundsInRoot
            val share = compose.onNodeWithContentDescription(shareLabel).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            if (compose.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                assertTrue(share.right <= header.left)
            } else assertTrue(share.left >= header.right)

            compose.onNodeWithTag("result_list").performScrollToNode(hasContentDescription(copyLabel))
            compose.onNodeWithContentDescription(copyLabel).performClick()
            compose.runOnIdle {
                val clip = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip
                assertNotNull(clip)
                assertEquals(vm.result.value!!.invoice.value(2), clip!!.getItemAt(0).text.toString())
            }
            compose.onNodeWithTag("result_list").performScrollToNode(hasContentDescription(shareLabel))
            compose.onNodeWithContentDescription(shareLabel).performClick()
            compose.waitUntil(10000) { !compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
            compose.waitUntil(10000) { compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            compose.onNodeWithContentDescription(shareLabel).assertIsDisplayed()
        } finally {
            val scope = vm.viewModelScope.coroutineContext[Job]!!
            compose.activityRule.scenario.close()
            scope.join()
            val db = ScanDatabase.open(context)
            try { id?.let { db.scans().delete(it) } } finally { db.close() }
        }
    }
}
