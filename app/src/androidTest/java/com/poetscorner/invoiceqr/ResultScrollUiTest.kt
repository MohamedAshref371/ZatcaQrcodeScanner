package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.openScanDatabase
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultScrollUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var db: ScanDatabase
    private lateinit var vm: ScannerViewModel
    private val ids = mutableListOf<Long>()
    private val secondSeller = "Result scroll second ${System.nanoTime()}"

    private fun text(id: Int) = compose.activity.getString(id)
    private fun tab(id: Int) = compose.onNode(
        (hasText(text(id)) or hasContentDescription(text(id))) and hasClickAction())
    private fun position() = compose.onNodeWithTag("result_list").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun titleHeight() = compose.onNodeWithTag("app_title_bar").fetchSemanticsNode().boundsInRoot.height

    @Before fun openFirstResult() = runBlocking<Unit> {
        db = openScanDatabase(compose.activity.applicationContext)
        ids += db.scans().saveIfNew(historyFixture(103001,
            seller = "Result scroll first ${System.nanoTime()}", hash = false)).scan.id
        ids += db.scans().saveIfNew(historyFixture(103002, seller = secondSeller, hash = false)).scan.id
        vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        compose.runOnIdle {
            // Fixtures are inserted through a separate Room instance; refresh the pager explicitly.
            vm.retryHistory()
            vm.open(ids.first())
        }
        compose.waitUntil(10_000) { vm.result.value?.scanId == ids.first() && !vm.restoringResult.value }
        compose.onNodeWithTag("result_list").assertIsDisplayed()
    }

    @After fun cleanUp() = runBlocking<Unit> {
        if (::vm.isInitialized) {
            val job = vm.viewModelScope.coroutineContext[Job]!!
            compose.activityRule.scenario.close()
            job.join()
        }
        if (::db.isInitialized) {
            ids.forEach { db.scans().delete(it) }
            db.close()
        }
    }

    @Test fun returningThroughTabKeepsScrollTitleAndExpandedDetails() {
        compose.onNodeWithTag("result_list").performScrollToNode(hasText(text(R.string.check_details)))
        compose.onNodeWithText(text(R.string.check_details)).performClick()
        compose.onNodeWithTag("result_list").performTouchInput { swipeUp() }
        val savedPosition = position()
        val savedTitleHeight = titleHeight()
        assertTrue(savedPosition > 0f)

        tab(R.string.nav_settings).performClick()
        tab(R.string.nav_result).performClick()
        assertEquals("Result scroll should survive tab navigation", savedPosition, position(), 0.01f)
        assertEquals("Result title should keep its position", savedTitleHeight, titleHeight(), 1f)
        compose.onNodeWithTag("result_list").performScrollToNode(hasText(text(R.string.result_hide_checks)))
        compose.onNodeWithText(text(R.string.result_hide_checks)).assertIsDisplayed()
    }

    @Test fun openingAnotherHistoryCardStartsResultAtTop() {
        compose.onNodeWithTag("result_list").performTouchInput { swipeUp() }
        assertTrue(position() > 0f)
        tab(R.string.nav_history).performClick()
        compose.waitUntil(10_000) {
            !vm.historyState.value.loading && vm.historyState.value.entries.any { it.id == ids.last() }
        }
        val cardIndex = vm.historyState.value.entries.indexOfFirst { it.id == ids.last() }
        val cardTag = "history_card_$cardIndex"
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag(cardTag))
        val sharedTitleHeight = titleHeight()
        compose.onNodeWithTag(cardTag).performClick()
        compose.waitUntil(10_000) { vm.result.value?.scanId == ids.last() && !vm.restoringResult.value }
        assertEquals("A new invoice starts at the beginning", 0f, position(), 0.01f)
        assertEquals("A new invoice keeps the shared title position", sharedTitleHeight, titleHeight(), 1f)
        compose.onNodeWithContentDescription(text(R.string.share_invoice)).assertIsDisplayed()
    }
}
