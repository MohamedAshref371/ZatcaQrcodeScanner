package com.poetscorner.invoiceqr.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Store filters independently of the invoice database and of temporary navigation state. */
internal class StatisticsFilterRepository(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("statistics_filters", Context.MODE_PRIVATE))
    val hasSavedFilters get() = preferences.contains("seller")

    fun load(today: LocalDate = LocalDate.now()): StatisticsRequest {
        val includedVat = preferences.getString("vat", "").orEmpty()
        val excludedVat = preferences.getString("excluded_vat", "").orEmpty()
        val mode = StatisticsVatMode.entries.firstOrNull { it.name == preferences.getString("vat_mode", null) }
            ?: if (excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else StatisticsVatMode.INCLUDE
        val preset = StatisticsPreset.entries.firstOrNull { it.name == preferences.getString("date_preset", null) }
        var from = preferences.getString("from_day", null)?.toLongOrNull()
        var to = preferences.getString("to_day", null)?.toLongOrNull()
        if (from != null && to != null && from > to) { from = null; to = null }
        return StatisticsRequest(seller = preferences.getString("seller", "").orEmpty(),
            fromDay = from, toDay = to, datePreset = preset)
            .withVatFilter(mode, if (mode == StatisticsVatMode.EXCLUDE) excludedVat else includedVat).resolvePeriod(today)
    }

    fun save(request: StatisticsRequest) {
        val mode = if (request.excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else request.vatMode
        val filter = request.withVatFilter(mode, if (mode == StatisticsVatMode.EXCLUDE) request.excludedVat else request.vat)
        // apply() updates memory immediately and Android flushes its queued write on lifecycle changes.
        preferences.edit().putString("seller", request.seller).putString("vat", filter.vat)
            .putString("excluded_vat", filter.excludedVat).putString("vat_mode", filter.vatMode.name)
            .putString("date_preset", request.datePreset?.name)
            .putString("from_day", if (request.datePreset == null) request.fromDay?.toString() else null)
            .putString("to_day", if (request.datePreset == null) request.toDay?.toString() else null)
            .apply()
    }
}
