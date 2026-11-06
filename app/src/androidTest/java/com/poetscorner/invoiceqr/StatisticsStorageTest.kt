package com.poetscorner.invoiceqr

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class StatisticsStorageTest {
    private lateinit var db: ScanDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ScanDatabase::class.java).build()
    }
    @After fun close() { db.close() }

    private suspend fun calculate(request: StatisticsRequest) = db.statistics(request)

    @Test fun sellerSuggestionsMatchSubstringsWithoutDuplicatesAndLimitResults() = runBlocking {
        db.scans().insertBatch((1..8).map { historyFixture(it, seller = "North Market $it") } +
            listOf(historyFixture(9, seller = "North Market 1"),
                historyFixture(10, seller = "South"), historyFixture(11, seller = "North %_")))
        assertEquals((1..5).map { "North Market $it" }, db.scans().sellerSuggestions("market"))
        assertEquals(listOf("North Market 1"), db.scans().sellerSuggestions("market 1"))
        assertEquals(listOf("North %_"), db.scans().sellerSuggestions("%_"))
        assertTrue(db.scans().sellerSuggestions("").isEmpty())
        assertTrue(db.scans().sellerSuggestions("missing").isEmpty())
    }

    @Test fun readsEveryPageAndIncludesFailedInvoicesAndIndependentValidAmounts() = runBlocking {
        db.scans().insertBatch((1..450).map { historyFixture(it, seller = "Market", hash = false) })
        val invalid = historyFixture(451, seller = "Market", date = "invalid", hash = false)
        db.scans().insert(invalid.copy(details = invalid.details.copy(total = "invalid", tax = "1.00")))
        val result = calculate(StatisticsRequest())
        assertEquals(451, result.summary.count)
        assertEquals(BigDecimal("51750.00"), result.summary.total)
        assertEquals(BigDecimal("6751.00"), result.summary.tax)
        assertEquals(1, result.summary.missingTotal)
        val dates = calculate(StatisticsRequest(grouping = StatisticsGrouping.DAY))
        assertEquals(1, dates.groups.last().totals.count)
        assertTrue(dates.groups.last().key.unknown)
    }

    @Test fun filtersAreAndedLiteralAndInclusiveAndUnknownDatesCannotMatchADateRange() = runBlocking {
        db.scans().insertBatch(listOf(
            historyFixture(1, seller = "North %_", date = "2026-10-01T23:59:59Z"),
            historyFixture(2, seller = "North %_", date = "2026-10-02T00:00:00"),
            historyFixture(3, seller = "South", date = "2026-10-02T12:00:00Z"),
            historyFixture(4, seller = "North %_", date = "invalid")))
        assertEquals(2, calculate(StatisticsRequest(fromDay = historyBaseDay, toDay = historyBaseDay + 1, seller = "NORTH")).summary.count)
        assertEquals(1, calculate(StatisticsRequest(fromDay = historyBaseDay + 1, toDay = historyBaseDay + 1,
            seller = "%_", vat = "0002")).summary.count)
        assertEquals(0, calculate(StatisticsRequest(seller = "South", vat = "0002")).summary.count)
        assertEquals(1, calculate(StatisticsRequest(vat = "٠٠٠٢")).summary.count)
        assertEquals(0, calculate(StatisticsRequest(seller = "' OR 1=1 --")).summary.count)
        assertEquals(3, calculate(StatisticsRequest(seller = "%_")).summary.count)
        assertEquals(3, calculate(StatisticsRequest(toDay = historyBaseDay + 1)).summary.count)
        val excluded = "300000000000023"
        assertEquals(3, calculate(StatisticsRequest(excludedVat = excluded)).summary.count)
        assertEquals(3, calculate(StatisticsRequest(excludedVat = "٣٠٠٠٠٠٠٠٠٠٠٠٠٢٣")).summary.count)
        assertEquals(0, calculate(StatisticsRequest(vat = excluded, excludedVat = excluded)).summary.count)
        assertEquals(3, calculate(StatisticsRequest(excludedVat = "0002")).summary.count)
        assertEquals(0, calculate(StatisticsRequest(excludedVat = "300")).summary.count)
        assertEquals(4, calculate(StatisticsRequest(excludedVat = "")).summary.count)
        assertEquals(4, calculate(StatisticsRequest(excludedVat = "%_")).summary.count)
    }

    @Test fun exclusionAlsoRecognizesArabicDigitsStoredInAnInvoiceThatFailedValidation() = runBlocking {
        val row = historyFixture(77, seller = "Own invoice", hash = false)
        val arabicVat = row.details.vat.map { (0x0660 + it.digitToInt()).toChar() }.joinToString("")
        db.scans().insert(row.copy(passed = false, details = row.details.copy(vat = arabicVat)))
        assertEquals(1, calculate(StatisticsRequest()).summary.count)
        val request = StatisticsRequest(excludedVat = row.details.vat)
        assertEquals(0, calculate(request).summary.count)
        assertEquals(0, calculate(StatisticsRequest(excludedVat = row.details.vat.takeLast(4))).summary.count)
        val selected = HistoryFilter(statisticsSelection = StatisticsSelection(request, StatisticsKey(text = "Own invoice")))
        assertEquals(0, db.scans().counts(HistoryQueries(selected).counts()).matching)
    }

    @Test fun openingEveryGroupReturnsExactlyItsInvoicesIncludingUnknownAndUnicodeTrimmedGroups() = runBlocking {
        db.scans().insertBatch(listOf(
            historyFixture(1, seller = "Market", date = "2026-04-01T23:59:59Z").copy(category = 1),
            historyFixture(2, seller = "Market Branch", date = "2026-05-01T00:00:00").copy(category = 2),
            historyFixture(3, seller = "\u00a0Market\t", date = "2026-06-30T12:00:00Z").copy(category = 1),
            historyFixture(4, seller = " ", date = "invalid"),
            historyFixture(5, seller = "Market", date = "2027-04-01T00:00:00Z").copy(category = 99)))
        val stored = db.scans().all().map { HistoryEntry.from(it) }
        val categories = calculate(StatisticsRequest(grouping = StatisticsGrouping.CATEGORY)).groups
        assertEquals(listOf("0", "1", "2", "99"), categories.map { it.key.text })
        assertEquals(listOf(1, 2, 1, 1), categories.map { it.totals.count })
        for (grouping in StatisticsGrouping.entries) {
            for (sellerFilter in listOf("", "Market")) for (excluded in listOf("", "300000000000023", "0002")) {
                val request = StatisticsRequest(grouping = grouping, seller = sellerFilter, excludedVat = excluded)
                for (group in calculate(request).groups) {
                    val filter = HistoryFilter(statisticsSelection = StatisticsSelection(request, group.key))
                    val queries = HistoryQueries(filter)
                    assertEquals(group.totals.count, db.scans().counts(queries.counts()).matching)
                    assertEquals(filter.apply(stored).map { it.scan.id }, db.scans().summaries(queries.page()).map { it.id })
                }
            }
        }
    }

    @Test fun groupBreakdownUsesEveryPageAndReconcilesWithTheDisplayedNet() = runBlocking {
        val added = (1..201).map { historyFixture(it, seller = "Market").copy(category = 1) }
        val subtracted = historyFixture(202, seller = "Market").copy(category = 2).let {
            it.copy(details = it.details.copy(total = "40.00", tax = "6.00"))
        }
        val ignored = historyFixture(203, seller = "Market").copy(category = 3).let {
            it.copy(details = it.details.copy(total = "25.00", tax = "4.00"))
        }
        val missing = historyFixture(204, seller = "Market").copy(category = 2).let {
            it.copy(details = it.details.copy(total = "invalid", tax = "2.00"))
        }
        db.scans().insertBatch(added + subtracted + ignored + missing +
            historyFixture(205, seller = "Other").copy(category = 1))
        val request = StatisticsRequest(seller = "Market")
            .withCategoryCalculation(2, StatisticsCategoryCalculation.SUBTRACT)
            .withCategoryCalculation(3, StatisticsCategoryCalculation.IGNORE)
        val group = calculate(request).groups.single()
        val breakdown = db.statisticsBreakdown(StatisticsSelection(request, group.key))
        assertEquals(group.totals, breakdown.net)
        assertEquals(204, breakdown.original.count)
        assertEquals(BigDecimal("23180.00"), breakdown.original.total)
        assertEquals(BigDecimal("3027.00"), breakdown.original.tax)
        assertEquals(BigDecimal("23075.00"), breakdown.net.total)
        assertEquals(BigDecimal("3007.00"), breakdown.net.tax)
        assertEquals(1, breakdown.net.ignoredCount)
        assertEquals(1, breakdown.net.missingTotal)
        assertEquals(listOf(1, 2, 3), breakdown.categories.map { it.category })
        assertEquals(BigDecimal("-40.00"), breakdown.categories[1].contribution.total)
        assertEquals(BigDecimal("0.00"), breakdown.categories[2].contribution.total)
    }
}
