package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.domain.ExactAmount
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.*

enum class StatisticsGrouping { SELLER, VAT, CATEGORY, DAY, MONTH, QUARTER, HALF_YEAR, YEAR }
enum class StatisticsSort { GROUP, COUNT, TOTAL, TAX }
enum class StatisticsVatMode { INCLUDE, EXCLUDE }
enum class StatisticsCategoryCalculation { ADD, SUBTRACT, IGNORE }
data class StatisticsRequest(
    val grouping: StatisticsGrouping = StatisticsGrouping.SELLER,
    val sort: StatisticsSort = StatisticsSort.GROUP,
    val descending: Boolean = false,
    val seller: String = "", val vat: String = "",
    val fromDay: Long? = null, val toDay: Long? = null,
    val excludedVat: String = "", val datePreset: StatisticsPreset? = null,
    val vatMode: StatisticsVatMode = if (excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else StatisticsVatMode.INCLUDE,
    val categoryCalculations: Map<Int, StatisticsCategoryCalculation> = emptyMap()
) : SavedStateValue {
    private companion object { const val serialVersionUID = 3314063135706167752L }

    init { require(fromDay == null || toDay == null || fromDay <= toDay) }
    val filtered get() = seller.isNotBlank() || vat.isNotBlank() || excludedVat.isNotBlank() || fromDay != null || toDay != null || datePreset != null
    val categoryCalculationActive get() = grouping != StatisticsGrouping.CATEGORY && categoryCalculations.isNotEmpty()
}

fun StatisticsRequest.calculationFor(category: Int): StatisticsCategoryCalculation =
    categoryCalculations[category] ?: StatisticsCategoryCalculation.ADD

fun StatisticsRequest.withCategoryCalculation(category: Int, calculation: StatisticsCategoryCalculation): StatisticsRequest {
    require(category in InvoiceCategories.ids)
    return copy(categoryCalculations = categoryCalculations.toMutableMap().apply {
        if (calculation == StatisticsCategoryCalculation.ADD) remove(category) else put(category, calculation)
    })
}

fun StatisticsRequest.withVatFilter(mode: StatisticsVatMode, number: String): StatisticsRequest {
    val normalized = normalizeVatNumber(number)
    return copy(vatMode = mode, vat = if (mode == StatisticsVatMode.INCLUDE) normalized else "",
        excludedVat = if (mode == StatisticsVatMode.EXCLUDE) normalized else "")
}

fun normalizeVatNumber(value: String): String = value.trim().map {
    when (it) {
        in '\u0660'..'\u0669' -> (it - '\u0660').digitToChar()
        in '\u06F0'..'\u06F9' -> (it - '\u06F0').digitToChar()
        else -> it
    }
}.joinToString("")

fun StatisticsRequest.resolvePeriod(today: LocalDate = today()): StatisticsRequest =
    datePreset?.let { withPeriod(it, today) } ?: this

data class StatisticsInvoice(val id: Long, val seller: String, val vat: String,
    val invoiceDay: Long?, val total: String, val tax: String, val category: Int = InvoiceCategories.NONE)
data class StatisticsKey(val text: String? = null, val periodStart: Long? = null) : SavedStateValue {
    private companion object { const val serialVersionUID = -7878493686484506581L }

    val unknown get() = text == null && periodStart == null
}

data class StatisticsSelection(val request: StatisticsRequest, val key: StatisticsKey) : SavedStateValue {
    private companion object { const val serialVersionUID = 4886106675832219751L }
}
internal fun StatisticsSelection.matches(seller: String, vat: String, day: Long?, category: Int = InvoiceCategories.NONE): Boolean {
    val vatTerm = request.vat.trim().normalizeDigits()
    if (!seller.contains(request.seller.trim(), ignoreCase = true) || !vat.contains(vatTerm)) return false
    val excluded = normalizeVatNumber(request.excludedVat)
    if (excluded.isNotEmpty() && normalizeVatNumber(vat).contains(excluded)) return false
    if (request.fromDay != null && (day == null || day < request.fromDay)) return false
    if (request.toDay != null && (day == null || day > request.toDay)) return false
    return when (request.grouping) {
        StatisticsGrouping.SELLER -> seller.trim().ifEmpty { null } == key.text
        StatisticsGrouping.VAT -> vat.trim().ifEmpty { null } == key.text
        StatisticsGrouping.CATEGORY -> category.toString() == key.text
        else -> if (key.periodStart == null) day == null else day != null &&
            day >= key.periodStart && day <= request.grouping.periodEnd(dateFromEpochDay(key.periodStart)).toEpochDays()
    }
}
enum class StatisticsPreset { THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER, THIS_YEAR }

fun StatisticsRequest.withPeriod(preset: StatisticsPreset, today: LocalDate = today()): StatisticsRequest {
    val first = when (preset) {
        StatisticsPreset.THIS_MONTH -> LocalDate(today.year, today.month, 1)
        StatisticsPreset.LAST_MONTH -> LocalDate(today.year, today.month, 1).minus(1, DateTimeUnit.MONTH)
        StatisticsPreset.THIS_QUARTER, StatisticsPreset.LAST_QUARTER ->
            LocalDate(today.year, (today.month.number - 1) / 3 * 3 + 1, 1).let {
                if (preset == StatisticsPreset.LAST_QUARTER) it.minus(3, DateTimeUnit.MONTH) else it
            }
        StatisticsPreset.THIS_YEAR -> LocalDate(today.year, 1, 1)
    }
    val end = when (preset) {
        StatisticsPreset.THIS_QUARTER, StatisticsPreset.LAST_QUARTER -> first.plus(3, DateTimeUnit.MONTH)
        StatisticsPreset.THIS_YEAR -> first.plus(1, DateTimeUnit.YEAR)
        else -> first.plus(1, DateTimeUnit.MONTH)
    }.minus(1, DateTimeUnit.DAY)
    return copy(fromDay = first.toEpochDays(), toDay = end.toEpochDays(), datePreset = preset)
}

internal fun StatisticsGrouping.periodEnd(start: LocalDate): LocalDate = when (this) {
    StatisticsGrouping.DAY -> start.plus(1, DateTimeUnit.DAY)
    StatisticsGrouping.MONTH -> start.plus(1, DateTimeUnit.MONTH)
    StatisticsGrouping.QUARTER -> start.plus(3, DateTimeUnit.MONTH)
    StatisticsGrouping.HALF_YEAR -> start.plus(6, DateTimeUnit.MONTH)
    StatisticsGrouping.YEAR -> start.plus(1, DateTimeUnit.YEAR)
    else -> error("Not a calendar grouping")
}.minus(1, DateTimeUnit.DAY)
data class StatisticsTotals(val count: Int = 0, val total: ExactAmount = ExactAmount.ZERO,
    val tax: ExactAmount = ExactAmount.ZERO, val missingTotal: Int = 0, val missingTax: Int = 0,
    val ignoredCount: Int = 0)
data class StatisticsGroup(val key: StatisticsKey, val totals: StatisticsTotals)
data class StatisticsResult(val summary: StatisticsTotals = StatisticsTotals(), val groups: List<StatisticsGroup> = emptyList())
data class StatisticsState(val request: StatisticsRequest = StatisticsRequest(),
    val result: StatisticsResult = StatisticsResult(), val loading: Boolean = true, val failed: Boolean = false,
    val resultRequest: StatisticsRequest = request, val hasResult: Boolean = !loading && !failed)

/** Bound, independent predicates: all filters must match. No pass-status or deduplication predicate. */
private val statisticsWhitespace = (Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isWhitespace() }.joinToString("")

internal fun statisticsConditions(request: StatisticsRequest, key: StatisticsKey? = null): Pair<MutableList<String>, MutableList<Any>> {
    val clauses = mutableListOf<String>()
    val args = mutableListOf<Any>()
    request.seller.trim().takeIf { it.isNotEmpty() }?.let {
        clauses += "instr(sellerSearch, ?) > 0"; args += it.lowercase()
    }
    request.vat.trim().takeIf { it.isNotEmpty() }?.let {
        clauses += "instr(vat, ?) > 0"
        args += it.normalizeDigits()
    }
    normalizeVatNumber(request.excludedVat).takeIf { it.isNotEmpty() }?.let {
        var normalizedColumn = "trim(vat, ?)"
        for (zero in listOf(0x0660, 0x06F0)) for (digit in 0..9) {
            normalizedColumn = "replace($normalizedColumn, '${(zero + digit).toChar()}', '$digit')"
        }
        clauses += "instr($normalizedColumn, ?) = 0"
        args += statisticsWhitespace; args += it
    }
    request.fromDay?.let { clauses += "invoiceDay >= ?"; args += it }
    request.toDay?.let { clauses += "invoiceDay <= ?"; args += it }
    if (key != null) {
        if (request.grouping == StatisticsGrouping.CATEGORY) {
            clauses += "category = ?"; args += (key.text?.toIntOrNull() ?: InvoiceCategories.NONE)
        } else if (request.grouping == StatisticsGrouping.SELLER || request.grouping == StatisticsGrouping.VAT) {
            val column = if (request.grouping == StatisticsGrouping.SELLER) "seller" else "vat"
            // Match Kotlin trim() for every Unicode whitespace character, including tabs and NBSP.
            clauses += "trim($column, ?) = ?"
            args += statisticsWhitespace
            args += key.text.orEmpty()
        } else if (key.periodStart == null) clauses += "invoiceDay IS NULL"
        else {
            clauses += "invoiceDay >= ? AND invoiceDay <= ?"
            args += key.periodStart
            args += request.grouping.periodEnd(dateFromEpochDay(key.periodStart)).toEpochDays()
        }
    }
    return clauses to args
}

fun statisticsQuery(request: StatisticsRequest, afterId: Long = 0, key: StatisticsKey? = null): BoundQuery {
    val (clauses, args) = statisticsConditions(request, key)
    clauses += "id > ?"; args += afterId
    return BoundQuery("SELECT id, seller, vat, invoiceDay, total, tax, category FROM scans WHERE " +
        clauses.joinToString(" AND ") + " ORDER BY id LIMIT 200", args)
}

/** Exact decimal accumulation; only grouped summaries and one input page are retained. */
class StatisticsAccumulator(private val request: StatisticsRequest) {
    private val groups = mutableMapOf<StatisticsKey, StatisticsTotals>()
    private var summary = StatisticsTotals()
    fun add(row: StatisticsInvoice) {
        val total = ExactAmount.parseInvoiceAmount(row.total)
        val tax = ExactAmount.parseInvoiceAmount(row.tax)
        val calculation = if (request.grouping == StatisticsGrouping.CATEGORY) StatisticsCategoryCalculation.ADD
            else request.calculationFor(row.category)
        fun contribution(amount: ExactAmount?): ExactAmount = when (calculation) {
            StatisticsCategoryCalculation.ADD -> amount ?: ExactAmount.ZERO
            StatisticsCategoryCalculation.SUBTRACT -> -(amount ?: ExactAmount.ZERO)
            StatisticsCategoryCalculation.IGNORE -> ExactAmount.ZERO
        }
        fun StatisticsTotals.add() = copy(count = count + 1,
            total = this.total + contribution(total),
            tax = this.tax + contribution(tax),
            missingTotal = missingTotal + if (calculation != StatisticsCategoryCalculation.IGNORE && total == null) 1 else 0,
            missingTax = missingTax + if (calculation != StatisticsCategoryCalculation.IGNORE && tax == null) 1 else 0,
            ignoredCount = ignoredCount + if (calculation == StatisticsCategoryCalculation.IGNORE) 1 else 0)
        val key = when (request.grouping) {
            StatisticsGrouping.SELLER -> StatisticsKey(text = row.seller.trim().takeIf(String::isNotEmpty))
            StatisticsGrouping.VAT -> StatisticsKey(text = row.vat.trim().takeIf(String::isNotEmpty))
            StatisticsGrouping.CATEGORY -> StatisticsKey(text = row.category.toString())
            else -> {
                val day = row.invoiceDay?.let(::dateFromEpochDay)
                val start = day?.let { when (request.grouping) {
                    StatisticsGrouping.DAY -> it
                    StatisticsGrouping.MONTH -> LocalDate(it.year, it.month, 1)
                    StatisticsGrouping.QUARTER -> LocalDate(it.year, ((it.month.number - 1) / 3) * 3 + 1, 1)
                    StatisticsGrouping.HALF_YEAR -> LocalDate(it.year, if (it.month.number <= 6) 1 else 7, 1)
                    else -> LocalDate(it.year, 1, 1)
                } }
                StatisticsKey(periodStart = start?.toEpochDays())
            }
        }
        groups[key] = (groups[key] ?: StatisticsTotals()).add()
        summary = summary.add()
    }
    fun finish(): StatisticsResult {
        val byKey = Comparator<StatisticsGroup> { a, b ->
            when {
                a.key.periodStart != null && b.key.periodStart != null -> a.key.periodStart.compareTo(b.key.periodStart)
                else -> a.key.text.orEmpty().compareTo(b.key.text.orEmpty(), ignoreCase = true)
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

/** Accumulate in keyset order while the caller holds its database snapshot. */
suspend fun readStatistics(request: StatisticsRequest, page: suspend (Long) -> List<StatisticsInvoice>): StatisticsResult {
    val accumulator = StatisticsAccumulator(request)
    var afterId = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val rows = page(afterId)
        if (rows.isEmpty()) return accumulator.finish()
        rows.forEach(accumulator::add)
        afterId = rows.last().id
    }
}
