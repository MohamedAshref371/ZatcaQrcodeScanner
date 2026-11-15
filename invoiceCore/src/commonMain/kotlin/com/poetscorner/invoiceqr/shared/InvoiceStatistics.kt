package com.poetscorner.invoiceqr.shared

import kotlinx.datetime.LocalDate

enum class StatisticsGrouping { SELLER, VAT, CATEGORY, DAY, MONTH, QUARTER, HALF_YEAR, YEAR }
enum class StatisticsSort { GROUP, COUNT, TOTAL, TAX }
enum class StatisticsCategoryCalculation { ADD, SUBTRACT, IGNORE }

data class InvoiceStatisticsOptions(
    val grouping: StatisticsGrouping = StatisticsGrouping.SELLER,
    val sort: StatisticsSort = StatisticsSort.GROUP,
    val descending: Boolean = false,
    val categoryCalculations: Map<Int, StatisticsCategoryCalculation> = emptyMap(),
)

data class InvoiceStatisticsKey(val text: String? = null, val periodStart: Long? = null) {
    val unknown get() = text == null && periodStart == null
}

data class InvoiceStatisticsTotals(
    val count: Int = 0,
    val total: InvoiceAmount = InvoiceAmount.ZERO,
    val tax: InvoiceAmount = InvoiceAmount.ZERO,
    val missingTotal: Int = 0,
    val missingTax: Int = 0,
    val ignoredCount: Int = 0,
)

data class InvoiceStatisticsGroup(val key: InvoiceStatisticsKey, val totals: InvoiceStatisticsTotals)
data class InvoiceStatisticsResult(val summary: InvoiceStatisticsTotals, val groups: List<InvoiceStatisticsGroup>)

/** Groups one row at a time so callers can read a consistent database snapshot in pages. */
class InvoiceStatisticsAccumulator(private val options: InvoiceStatisticsOptions) {
    private val groups = mutableMapOf<InvoiceStatisticsKey, InvoiceStatisticsTotals>()
    private var summary = InvoiceStatisticsTotals()

    fun add(seller: String, vat: String, invoiceDay: Long?, total: String, tax: String, category: Int) {
        val calculation = if (options.grouping == StatisticsGrouping.CATEGORY) StatisticsCategoryCalculation.ADD
            else options.categoryCalculations[category] ?: StatisticsCategoryCalculation.ADD
        val ignored = calculation == StatisticsCategoryCalculation.IGNORE
        val totalAmount = if (ignored) null else InvoiceAmount.parse(total)
        val taxAmount = if (ignored) null else InvoiceAmount.parse(tax)
        val subtract = calculation == StatisticsCategoryCalculation.SUBTRACT
        val totalChange = totalAmount?.let { if (subtract) -it else it } ?: InvoiceAmount.ZERO
        val taxChange = taxAmount?.let { if (subtract) -it else it } ?: InvoiceAmount.ZERO

        fun InvoiceStatisticsTotals.add() = copy(
            count = count + 1, total = this.total + totalChange, tax = this.tax + taxChange,
            missingTotal = missingTotal + if (!ignored && totalAmount == null) 1 else 0,
            missingTax = missingTax + if (!ignored && taxAmount == null) 1 else 0,
            ignoredCount = ignoredCount + if (ignored) 1 else 0,
        )

        val key = when (options.grouping) {
            StatisticsGrouping.SELLER -> InvoiceStatisticsKey(text = seller.trim().ifEmpty { null })
            StatisticsGrouping.VAT -> InvoiceStatisticsKey(text = vat.trim().ifEmpty { null })
            StatisticsGrouping.CATEGORY -> InvoiceStatisticsKey(text = category.toString())
            else -> InvoiceStatisticsKey(periodStart = invoiceDay?.let { periodStart(LocalDate.fromEpochDays(it)) })
        }
        groups[key] = (groups[key] ?: InvoiceStatisticsTotals()).add()
        summary = summary.add()
    }

    private fun periodStart(day: LocalDate): Long {
        val start = when (options.grouping) {
            StatisticsGrouping.DAY -> day
            StatisticsGrouping.MONTH -> LocalDate(day.year, day.monthNumber, 1)
            StatisticsGrouping.QUARTER -> LocalDate(day.year, (day.monthNumber - 1) / 3 * 3 + 1, 1)
            StatisticsGrouping.HALF_YEAR -> LocalDate(day.year, if (day.monthNumber <= 6) 1 else 7, 1)
            else -> LocalDate(day.year, 1, 1)
        }
        return start.toEpochDays()
    }

    fun finish(): InvoiceStatisticsResult {
        val byKey = Comparator<InvoiceStatisticsGroup> { a, b ->
            val first = a.key.periodStart
            val second = b.key.periodStart
            if (first != null && second != null) first.compareTo(second)
            else a.key.text.orEmpty().compareTo(b.key.text.orEmpty(), ignoreCase = true)
        }
        val selected = when (options.sort) {
            StatisticsSort.GROUP -> byKey
            StatisticsSort.COUNT -> compareBy<InvoiceStatisticsGroup> { it.totals.count }
            StatisticsSort.TOTAL -> compareBy { it.totals.total }
            StatisticsSort.TAX -> compareBy { it.totals.tax }
        }
        val direction = if (options.descending) selected.reversed() else selected
        val comparator = if (options.sort == StatisticsSort.GROUP)
            compareBy<InvoiceStatisticsGroup> { it.key.unknown }.then(direction) else direction.then(byKey)
        return InvoiceStatisticsResult(summary, groups.map { InvoiceStatisticsGroup(it.key, it.value) }.sortedWith(comparator))
    }
}
