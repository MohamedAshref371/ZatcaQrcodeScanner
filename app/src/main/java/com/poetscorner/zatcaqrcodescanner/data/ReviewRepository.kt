package com.poetscorner.zatcaqrcodescanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.ZoneId

private val Context.reviewStore by preferencesDataStore("review_usage")

internal data class ReviewUsage(
    val firstUsedAt: Long? = null,
    val activeDays: Set<String> = emptySet(),
    val invoiceKeys: Set<String> = emptySet(),
    val attempted: Boolean = false
) {
    fun eligible(now: Long): Boolean = !attempted && firstUsedAt != null &&
        now >= firstUsedAt && now - firstUsedAt >= 7L * 24 * 60 * 60 * 1000 &&
        activeDays.size >= 3 && invoiceKeys.size >= 10

    fun used(now: Long, day: String): ReviewUsage = copy(
        firstUsedAt = firstUsedAt ?: now,
        activeDays = if (activeDays.size < 3) activeDays + day else activeDays
    )

    fun saved(key: String): ReviewUsage = copy(
        invoiceKeys = if (invoiceKeys.size < 10) invoiceKeys + key else invoiceKeys
    )
}

/** Independent of history deletion; stores at most ten payload hashes and three dates. */
class ReviewRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.reviewStore)

    suspend fun recordUse(now: Long = System.currentTimeMillis()) {
        store.edit { p -> write(p, read(p).used(now, localDay(now))) }
    }

    suspend fun recordSavedInvoice(key: String, now: Long = System.currentTimeMillis()) {
        store.edit { p -> write(p, read(p).used(now, localDay(now)).saved(key)) }
    }

    private fun localDay(now: Long) = Instant.ofEpochMilli(now)
        .atZone(ZoneId.systemDefault()).toLocalDate().toString()

    // Claim atomically and persist BEFORE calling Play, including when Play shows nothing.
    suspend fun claimAttempt(now: Long = System.currentTimeMillis()): Boolean {
        var claimed = false
        store.edit { p ->
            val usage = read(p)
            if (usage.eligible(now)) {
                write(p, usage.copy(attempted = true))
                claimed = true
            }
        }
        return claimed
    }

    private fun read(p: Preferences) = ReviewUsage(p[FIRST_USED], p[DAYS].orEmpty(),
        p[INVOICES].orEmpty(), p[ATTEMPTED] ?: false)

    private fun write(p: MutablePreferences, usage: ReviewUsage) {
        usage.firstUsedAt?.let { p[FIRST_USED] = it }
        p[DAYS] = usage.activeDays
        p[INVOICES] = usage.invoiceKeys
        p[ATTEMPTED] = usage.attempted
    }

    private companion object {
        val FIRST_USED = longPreferencesKey("first_used_at")
        val DAYS = stringSetPreferencesKey("active_days")
        val INVOICES = stringSetPreferencesKey("invoice_keys")
        val ATTEMPTED = booleanPreferencesKey("attempted")
    }
}
