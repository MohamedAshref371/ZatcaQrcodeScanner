package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import kotlin.test.*
import com.poetscorner.invoiceqr.domain.ExactAmount

class HistoryDisplayTest {
    @Test fun newSearchKeepsCardsAndSummaryBeforeThePagerReceivesTheFilter() {
        val oldFilter = HistoryFilter("previous")
        val ready = HistoryPageState(filter = oldFilter,
            entries = listOf(HistorySummary(1, "Previous seller", "3001", "115.00", 1, true, 1)),
            total = 100, matching = 1, loading = false, amount = ExactAmount.parseInvoiceAmount("115.00"))
        val pending = ready.forDisplay(HistoryFilter("next"))
        assertSame(ready.entries, pending.entries)
        assertEquals(ready.amount, pending.amount)
        assertEquals(ready.matching, pending.matching)
        assertEquals(oldFilter, pending.displayedFilter)
        assertTrue(pending.loading)
        assertFalse(pending.canExport)
        assertEquals(HistoryFilter("next"), pending.filter)
        assertSame(ready, ready.forDisplay(oldFilter))
    }
}
