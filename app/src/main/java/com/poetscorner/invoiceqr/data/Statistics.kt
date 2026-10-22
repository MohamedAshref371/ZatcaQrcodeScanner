package com.poetscorner.invoiceqr.data

import androidx.sqlite.db.SimpleSQLiteQuery
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

enum class StatisticsGrouping { SELLER, VAT, DAY, MONTH, QUARTER, HALF_YEAR, YEAR }
enum class StatisticsSort { GROUP, COUNT, TOTAL, TAX }
data class StatisticsRequest(
    val grouping: StatisticsGrouping = StatisticsGrouping.SELLER,
    val sort: StatisticsSort = StatisticsSort.GROUP,
    val descending: Boolean = false,
    val seller: String = "", val vat: String = "",
    val fromDay: Long? = null, val toDay: Long? = null
) : Serializable {
    init { require(fromDay == null || toDay == null || fromDay <= toDay) }
    val filtered get() = seller.isNotBlank() || vat.isNotBlank() || fromDay != null || toDay != null
}

data class StatisticsInvoice(val id: Long, val seller: String, val vat: String,
    val invoiceDay: Long?, val total: String, val tax: String)
data class StatisticsKey(val text: String? = null, val periodStart: Long? = null) : Serializable {
    val unknown get() = text == null && periodStart == null
}

data class StatisticsSelection(val request: StatisticsRequest, val key: StatisticsKey) : Serializable
internal fun StatisticsSelection.matches(seller: String, vat: String, day: Long?): Boolean {
    val vatTerm = request.vat.trim().map { if (it.isDigit()) Character.digit(it, 10).digitToChar() else it }.joinToString("")
    if (!seller.contains(request.seller.trim(), ignoreCase = true) || !vat.contains(vatTerm)) return false
    if (request.fromDay != null && (day == null || day < request.fromDay)) return false
    if (request.toDay != null && (day == null || day > request.toDay)) return false
    return when (request.grouping) {
        StatisticsGrouping.SELLER -> seller.trim().ifEmpty { null } == key.text
        StatisticsGrouping.VAT -> vat.trim().ifEmpty { null } == key.text
        else -> if (key.periodStart == null) day == null else day != null &&
            day >= key.periodStart && day <= request.grouping.periodEnd(LocalDate.ofEpochDay(key.periodStart)).toEpochDay()
    }
}
enum class StatisticsPreset { THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER, THIS_YEAR }

fun StatisticsRequest.withPeriod(preset: StatisticsPreset, today: LocalDate = LocalDate.now()): StatisticsRequest {
    val first = when (preset) {
        StatisticsPreset.THIS_MONTH -> today.withDayOfMonth(1)
        StatisticsPreset.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1)
        StatisticsPreset.THIS_QUARTER, StatisticsPreset.LAST_QUARTER ->
            LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1).let {
                if (preset == StatisticsPreset.LAST_QUARTER) it.minusMonths(3) else it
            }
        StatisticsPreset.THIS_YEAR -> today.withDayOfYear(1)
    }
    val end = when (preset) {
        StatisticsPreset.THIS_QUARTER, StatisticsPreset.LAST_QUARTER -> first.plusMonths(3)
        StatisticsPreset.THIS_YEAR -> first.plusYears(1)
        else -> first.plusMonths(1)
    }.minusDays(1)
    return copy(fromDay = first.toEpochDay(), toDay = end.toEpochDay())
}

internal fun StatisticsGrouping.periodEnd(start: LocalDate): LocalDate = when (this) {
    StatisticsGrouping.DAY -> start.plusDays(1)
    StatisticsGrouping.MONTH -> start.plusMonths(1)
    StatisticsGrouping.QUARTER -> start.plusMonths(3)
    StatisticsGrouping.HALF_YEAR -> start.plusMonths(6)
    StatisticsGrouping.YEAR -> start.plusYears(1)
    else -> error("Not a calendar grouping")
}.minusDays(1)
data class StatisticsTotals(val count: Int = 0, val total: BigDecimal = BigDecimal.ZERO.setScale(2),
    val tax: BigDecimal = BigDecimal.ZERO.setScale(2), val missingTotal: Int = 0, val missingTax: Int = 0)
data class StatisticsGroup(val key: StatisticsKey, val totals: StatisticsTotals)
data class StatisticsResult(val summary: StatisticsTotals = StatisticsTotals(), val groups: List<StatisticsGroup> = emptyList())
data class StatisticsState(val request: StatisticsRequest = StatisticsRequest(),
    val result: StatisticsResult = StatisticsResult(), val loading: Boolean = true, val failed: Boolean = false)

/** Bound, independent predicates: all filters must match. No pass-status or deduplication predicate. */
private val statisticsWhitespace = (Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isWhitespace() }.joinToString("")

internal fun statisticsConditions(request: StatisticsRequest, key: StatisticsKey? = null): Pair<MutableList<String>, MutableList<Any>> {
    val clauses = mutableListOf<String>()
    val args = mutableListOf<Any>()
    request.seller.trim().takeIf { it.isNotEmpty() }?.let {
        clauses += "instr(sellerSearch, ?) > 0"; args += it.lowercase(Locale.ROOT)
    }
    request.vat.trim().takeIf { it.isNotEmpty() }?.let {
        clauses += "instr(vat, ?) > 0"
        args += it.map { c -> if (c.isDigit()) Character.digit(c, 10).digitToChar() else c }.joinToString("")
    }
    request.fromDay?.let { clauses += "invoiceDay >= ?"; args += it }
    request.toDay?.let { clauses += "invoiceDay <= ?"; args += it }
    if (key != null) {
        if (request.grouping == StatisticsGrouping.SELLER || request.grouping == StatisticsGrouping.VAT) {
            val column = if (request.grouping == StatisticsGrouping.SELLER) "seller" else "vat"
            // Match Kotlin trim() for every Unicode whitespace character, including tabs and NBSP.
            clauses += "trim($column, ?) = ?"
            args += statisticsWhitespace
            args += key.text.orEmpty()
        } else if (key.periodStart == null) clauses += "invoiceDay IS NULL"
        else {
            clauses += "invoiceDay >= ? AND invoiceDay <= ?"
            args += key.periodStart
            args += request.grouping.periodEnd(LocalDate.ofEpochDay(key.periodStart)).toEpochDay()
        }
    }
    return clauses to args
}

fun statisticsQuery(request: StatisticsRequest, afterId: Long = 0): SimpleSQLiteQuery {
    val (clauses, args) = statisticsConditions(request)
    clauses += "id > ?"; args += afterId
    return SimpleSQLiteQuery("SELECT id, seller, vat, invoiceDay, total, tax FROM scans WHERE " +
        clauses.joinToString(" AND ") + " ORDER BY id LIMIT 200", args.toTypedArray())
}

/** Exact decimal accumulation; only grouped summaries and one input page are retained. */
class StatisticsAccumulator(private val request: StatisticsRequest) {
    private val groups = mutableMapOf<StatisticsKey, StatisticsTotals>()
    private var summary = StatisticsTotals()
    private val amountPattern = Regex("[0-9]+(\\.[0-9]{1,2})?")
    private fun amount(value: String) = value.takeIf(amountPattern::matches)?.toBigDecimalOrNull()
    fun add(row: StatisticsInvoice) {
        val total = amount(row.total)
        val tax = amount(row.tax)
        fun StatisticsTotals.add() = copy(count = count + 1,
            total = this.total + (total ?: BigDecimal.ZERO), tax = this.tax + (tax ?: BigDecimal.ZERO),
            missingTotal = missingTotal + if (total == null) 1 else 0,
            missingTax = missingTax + if (tax == null) 1 else 0)
        val key = when (request.grouping) {
            StatisticsGrouping.SELLER -> StatisticsKey(text = row.seller.trim().takeIf(String::isNotEmpty))
            StatisticsGrouping.VAT -> StatisticsKey(text = row.vat.trim().takeIf(String::isNotEmpty))
            else -> {
                val day = row.invoiceDay?.let(LocalDate::ofEpochDay)
                val start = day?.let { when (request.grouping) {
                    StatisticsGrouping.DAY -> it
                    StatisticsGrouping.MONTH -> it.withDayOfMonth(1)
                    StatisticsGrouping.QUARTER -> LocalDate.of(it.year, ((it.monthValue - 1) / 3) * 3 + 1, 1)
                    StatisticsGrouping.HALF_YEAR -> LocalDate.of(it.year, if (it.monthValue <= 6) 1 else 7, 1)
                    else -> it.withDayOfYear(1)
                } }
                StatisticsKey(periodStart = start?.toEpochDay())
            }
        }
        groups[key] = (groups[key] ?: StatisticsTotals()).add()
        summary = summary.add()
    }
    fun finish(): StatisticsResult {
        val byKey = Comparator<StatisticsGroup> { a, b ->
            when {
                a.key.periodStart != null && b.key.periodStart != null -> a.key.periodStart.compareTo(b.key.periodStart)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.key.text.orEmpty(), b.key.text.orEmpty())
            }
        }
        val selected = when (request.sort) {
            StatisticsSort.GROUP -> byKey
            StatisticsSort.COUNT -> compareBy<StatisticsGroup> { it.totals.count }
            StatisticsSort.TOTAL -> compareBy { it.totals.total }
            StatisticsSort.TAX -> compareBy { it.totals.tax }
        }
        val direction = if (request.descending) selected.reversed() else selected
        val comparator = if (request.sort == StatisticsSort.GROUP)
            compareBy<StatisticsGroup> { it.key.unknown }.then(direction)
        else direction.then(byKey)
        return StatisticsResult(summary, groups.map { StatisticsGroup(it.key, it.value) }.sortedWith(comparator))
    }
}
