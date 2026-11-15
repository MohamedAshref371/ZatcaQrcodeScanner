package com.poetscorner.invoiceqr.data

import androidx.sqlite.db.SimpleSQLiteQuery
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsAccumulator
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsOptions
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsTotals
import com.poetscorner.invoiceqr.shared.StatisticsCategoryCalculation as SharedCategoryCalculation
import com.poetscorner.invoiceqr.shared.StatisticsGrouping as SharedGrouping
import com.poetscorner.invoiceqr.shared.StatisticsSort as SharedSort
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

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
) : Serializable {
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

internal fun StatisticsRequest.withVatFilter(mode: StatisticsVatMode, number: String): StatisticsRequest {
    val normalized = normalizeVatNumber(number)
    return copy(vatMode = mode, vat = if (mode == StatisticsVatMode.INCLUDE) normalized else "",
        excludedVat = if (mode == StatisticsVatMode.EXCLUDE) normalized else "")
}

internal fun normalizeVatNumber(value: String): String = value.trim().map {
    when (it) {
        in '\u0660'..'\u0669' -> (it - '\u0660').digitToChar()
        in '\u06F0'..'\u06F9' -> (it - '\u06F0').digitToChar()
        else -> it
    }
}.joinToString("")

fun StatisticsRequest.resolvePeriod(today: LocalDate = LocalDate.now()): StatisticsRequest =
    datePreset?.let { withPeriod(it, today) } ?: this

data class StatisticsInvoice(val id: Long, val seller: String, val vat: String,
    val invoiceDay: Long?, val total: String, val tax: String, val category: Int = InvoiceCategories.NONE)
data class StatisticsKey(val text: String? = null, val periodStart: Long? = null) : Serializable {
    val unknown get() = text == null && periodStart == null
}

data class StatisticsSelection(val request: StatisticsRequest, val key: StatisticsKey) : Serializable
internal fun StatisticsSelection.matches(seller: String, vat: String, day: Long?, category: Int = InvoiceCategories.NONE): Boolean {
    val vatTerm = request.vat.trim().map { if (it.isDigit()) Character.digit(it, 10).digitToChar() else it }.joinToString("")
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
    return copy(fromDay = first.toEpochDay(), toDay = end.toEpochDay(), datePreset = preset)
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
    val tax: BigDecimal = BigDecimal.ZERO.setScale(2), val missingTotal: Int = 0, val missingTax: Int = 0,
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
        clauses += "instr(sellerSearch, ?) > 0"; args += it.lowercase(Locale.ROOT)
    }
    request.vat.trim().takeIf { it.isNotEmpty() }?.let {
        clauses += "instr(vat, ?) > 0"
        args += it.map { c -> if (c.isDigit()) Character.digit(c, 10).digitToChar() else c }.joinToString("")
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
            args += request.grouping.periodEnd(LocalDate.ofEpochDay(key.periodStart)).toEpochDay()
        }
    }
    return clauses to args
}

fun statisticsQuery(request: StatisticsRequest, afterId: Long = 0, key: StatisticsKey? = null): SimpleSQLiteQuery {
    val (clauses, args) = statisticsConditions(request, key)
    clauses += "id > ?"; args += afterId
    return SimpleSQLiteQuery("SELECT id, seller, vat, invoiceDay, total, tax, category FROM scans WHERE " +
        clauses.joinToString(" AND ") + " ORDER BY id LIMIT 200", args.toTypedArray())
}

/** Android retains its BigDecimal result model for formatting, breakdowns and exports. */
class StatisticsAccumulator(request: StatisticsRequest) {
    private val accumulator = InvoiceStatisticsAccumulator(InvoiceStatisticsOptions(
        SharedGrouping.valueOf(request.grouping.name), SharedSort.valueOf(request.sort.name), request.descending,
        request.categoryCalculations.mapValues { SharedCategoryCalculation.valueOf(it.value.name) },
    ))

    fun add(row: StatisticsInvoice) = accumulator.add(row.seller, row.vat, row.invoiceDay, row.total, row.tax, row.category)

    fun finish(): StatisticsResult {
        val result = accumulator.finish()
        return StatisticsResult(result.summary.toAndroid(), result.groups.map {
            StatisticsGroup(StatisticsKey(it.key.text, it.key.periodStart), it.totals.toAndroid())
        })
    }

    private fun InvoiceStatisticsTotals.toAndroid() = StatisticsTotals(
        count, BigDecimal(total.toPlainString()), BigDecimal(tax.toPlainString()), missingTotal, missingTax, ignoredCount,
    )
}
