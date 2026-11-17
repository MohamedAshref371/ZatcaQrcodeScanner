package com.poetscorner.invoiceqr

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun scrollPosition(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun overviewAndGroupKeepTheirScrollAfterOpeningAnInvoiceAndRefreshingStatistics() = runBlocking<Unit> {
        val db = openScanDatabase(compose.activity)
        val inserted = mutableListOf<Long>()
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        val prefix = "Statistics scroll "
        val selected = prefix + "40"
        try {
            val fixtures = (1..60).map {
                historyFixture(450000 + it, seller = prefix + it.toString().padStart(2, '0'), hash = false)
            } + (1..60).map { historyFixture(451000 + it, seller = selected, hash = false) }
            fixtures.forEach {
                val saved = db.scans().saveIfNew(it)
                if (!saved.alreadyExists) inserted += saved.scan.id
            }
            withContext(Dispatchers.Main) {
                vm.updateStatistics(StatisticsRequest(seller = prefix))
                vm.navigate(4)
            }
            compose.waitUntil(10000) { !vm.statistics.value.loading && vm.statistics.value.result.groups.size == 60 }
            compose.onNodeWithTag("statistics_list").performScrollToNode(hasText(selected))
            val overviewPosition = scrollPosition("statistics_list")
            compose.onNodeWithText(selected).performClick()
            compose.waitUntil(10000) { !vm.statisticsInvoices.value.loading && vm.statisticsInvoices.value.matching == 61 }
            compose.onNodeWithTag("history_list").performScrollToIndex(35)
            val groupPosition = scrollPosition("history_list")
            val amount = compose.activity.getString(R.string.history_invoice_total, "115.00")
            compose.onAllNodes(hasClickAction() and hasText(amount) and hasAnyAncestor(hasTestTag("history_list")))
                .onFirst().performClick()
            compose.waitUntil(10000) { vm.screen.value == 1 && vm.result.value != null }
            // Exercise the statistics stream's five-second unsubscribe timeout as well as navigation.
            delay(5500)
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("history_list").assertIsDisplayed()
            assertEquals(groupPosition, scrollPosition("history_list"), 0.01f)
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(10000) { vm.statisticsSelection.value == null && !vm.statistics.value.loading }
            compose.onNodeWithTag("statistics_list").assertIsDisplayed()
            assertEquals(overviewPosition, scrollPosition("statistics_list"), 0.01f)
            compose.onNodeWithText(selected).assertIsDisplayed()
        } finally {
            withContext(Dispatchers.Main) {
                vm.closeStatisticsGroup()
                vm.navigate(0)
                vm.updateStatistics(StatisticsRequest())
            }
            inserted.forEach { db.scans().delete(it) }
            db.close()
        }
    }
}
