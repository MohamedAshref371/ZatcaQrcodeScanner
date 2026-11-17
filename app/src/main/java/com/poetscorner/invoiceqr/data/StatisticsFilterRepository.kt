package com.poetscorner.invoiceqr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

internal class StatisticsFilterRepository(private val preferences: SharedPreferences) : com.poetscorner.invoiceqr.ui.StatisticsFilters {
    constructor(context: Context) : this(context.getSharedPreferences("statistics_filters", Context.MODE_PRIVATE))
    private val store = StatisticsFilterStore(object : StringPreferences {
        override fun contains(key: String) = preferences.contains(key)
        override fun get(key: String, fallback: String?) = preferences.getString(key, fallback)
        override fun write(change: StringPreferencesWriter.() -> Unit) {
            preferences.edit { change(StringPreferencesWriter { key, value -> putString(key, value) }) }
        }
    })
    override val hasSavedFilters get() = store.hasSavedFilters
    override fun load() = store.load()
    fun load(today: LocalDate) = store.load(today.toKotlinLocalDate())
    override fun save(request: StatisticsRequest) = store.save(request)
}
