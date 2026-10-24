package com.poetscorner.invoiceqr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.ui.HistoryScreen
import com.poetscorner.invoiceqr.ui.StatisticsScreen
import com.poetscorner.invoiceqr.ui.theme.ZatcaQrcodeScannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SellerNameLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val seller = "YUOSRA ABDULKARIM EST."

    @Composable
    private fun Layout(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
            ZatcaQrcodeScannerTheme {
                Box(Modifier.width(400.dp)) { content() }
            }
        }
    }

    private fun assertSellerFitsOnOneLine(listTag: String) {
        compose.onNodeWithTag(listTag).performScrollToNode(hasText(seller))
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(seller, useUnmergedTree = true).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }

    @Test fun statisticsSellerUsesAvailableCardWidthInRtl() {
        compose.setContent { Layout {
            StatisticsScreen(StatisticsState(loading = false, result = StatisticsResult(
                groups = listOf(StatisticsGroup(StatisticsKey(text = seller), StatisticsTotals(count = 1))))), {}, {})
        } }
        assertSellerFitsOnOneLine("statistics_list")
    }

    @Test fun historySellerUsesAvailableCardWidthInRtl() {
        compose.setContent { Layout {
            HistoryScreen(HistoryPageState(loading = false, total = 1, matching = 1,
                entries = listOf(HistorySummary(1, seller, "", "100", 0, true, 1))), "", {}, {}, {}, {}, {})
        } }
        assertSellerFitsOnOneLine("history_list")
    }
}
