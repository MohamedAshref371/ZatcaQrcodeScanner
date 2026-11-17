package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.ui.StatisticsFilters
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

interface StringPreferences {
    fun contains(key: String): Boolean
    fun get(key: String, fallback: String?): String?
    fun write(change: StringPreferencesWriter.() -> Unit)
}
fun interface StringPreferencesWriter { fun put(key: String, value: String?) }

/** The same keys, defaults and validation on both platforms. */
class StatisticsFilterStore(private val preferences: StringPreferences) : StatisticsFilters {
    override val hasSavedFilters get() = preferences.contains("seller")

    override fun load(): StatisticsRequest = load(kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()))

    fun load(today: LocalDate): StatisticsRequest {
        val includedVat = preferences.get("vat", "").orEmpty()
        val excludedVat = preferences.get("excluded_vat", "").orEmpty()
        val mode = StatisticsVatMode.entries.firstOrNull { it.name == preferences.get("vat_mode", null) }
            ?: if (excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else StatisticsVatMode.INCLUDE
        val preset = StatisticsPreset.entries.firstOrNull { it.name == preferences.get("date_preset", null) }
        var from = preferences.get("from_day", null)?.toLongOrNull()
        var to = preferences.get("to_day", null)?.toLongOrNull()
        if (from != null && to != null && from > to) { from = null; to = null }
        val categoryCalculations = InvoiceCategories.ids.mapNotNull { category ->
            val calculation = StatisticsCategoryCalculation.entries.firstOrNull {
                it.name == preferences.get("category_calculation_$category", null)
            }
            if (calculation == null || calculation == StatisticsCategoryCalculation.ADD) null
            else category to calculation
        }.toMap()
        return StatisticsRequest(seller = preferences.get("seller", "").orEmpty(),
            fromDay = from, toDay = to, datePreset = preset, categoryCalculations = categoryCalculations)
            .withVatFilter(mode, if (mode == StatisticsVatMode.EXCLUDE) excludedVat else includedVat).resolvePeriod(today)
    }

    override fun save(request: StatisticsRequest) {
        val mode = if (request.excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else request.vatMode
        val filter = request.withVatFilter(mode, if (mode == StatisticsVatMode.EXCLUDE) request.excludedVat else request.vat)
        // The adapter preserves the platform preference store; key mapping stays shared.
        preferences.write {
            put("seller", request.seller)
            put("vat", filter.vat)
            put("excluded_vat", filter.excludedVat)
            put("vat_mode", filter.vatMode.name)
            put("date_preset", request.datePreset?.name)
            put("from_day", if (request.datePreset == null) request.fromDay?.toString() else null)
            put("to_day", if (request.datePreset == null) request.toDay?.toString() else null)
            InvoiceCategories.ids.forEach { category ->
                put("category_calculation_$category", request.categoryCalculations[category]?.name)
            }
        }
    }
}
