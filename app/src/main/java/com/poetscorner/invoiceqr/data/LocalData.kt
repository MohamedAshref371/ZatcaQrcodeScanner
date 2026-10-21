package com.poetscorner.invoiceqr.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.math.BigDecimal

enum class ScanEngine {
    ZXING, ML_KIT;

    companion object {
        fun fromPreference(value: String?): ScanEngine = entries.firstOrNull { it.name == value } ?: ZXING
    }
}

@Entity(tableName = "scans", indices = [Index(value = ["payloadKey"], unique = true),
    Index(value = ["scannedAt", "id"]), Index(value = ["invoiceDay", "scannedAt", "id"])])
data class ScanEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val raw: String,
    val seller: String, val scannedAt: Long = System.currentTimeMillis(), val passed: Boolean, val phase: Int,
    @ColumnInfo(defaultValue = "'STANDARD'") val invoiceType: String = "STANDARD",
    val payloadKey: String = invoicePayloadKey(raw),
    @Embedded val details: InvoiceDetails = InvoiceDetails.from(ZatcaParser.parse(raw), seller)) {
    fun revalidated(): ScanEntity {
        val invoice = ZatcaParser.parse(raw)
        return copy(passed = invoice.passed, phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name)
    }
    companion object {
        fun from(invoice: com.poetscorner.invoiceqr.domain.Invoice, checkedAt: Long) = ScanEntity(
            raw = invoice.raw, seller = invoice.value(1), scannedAt = checkedAt, passed = invoice.passed,
            phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name,
            details = InvoiceDetails.from(invoice, invoice.value(1)))
    }
}
@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY scannedAt DESC, id DESC") suspend fun all(): List<ScanEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(scan: ScanEntity): Long
    @Insert suspend fun insertBatch(scans: List<ScanEntity>)
    @Query("SELECT * FROM scans WHERE payloadKey = :key LIMIT 1") suspend fun byPayloadKey(key: String): ScanEntity?
    @Query("SELECT * FROM scans WHERE id = :id") suspend fun byId(id: Long): ScanEntity?
    @RawQuery(observedEntities = [ScanEntity::class]) fun observeCounts(query: SupportSQLiteQuery): Flow<HistoryCounts>
    @RawQuery suspend fun summaries(query: SupportSQLiteQuery): List<HistorySummary>
    @RawQuery suspend fun exportRows(query: SupportSQLiteQuery): List<ExportInvoice>
    @RawQuery suspend fun hasHash(query: SupportSQLiteQuery): Boolean
    @RawQuery suspend fun counts(query: SupportSQLiteQuery): HistoryCounts
    @RawQuery suspend fun amounts(query: SupportSQLiteQuery): List<HistoryAmount>
    /** One snapshot for the count, first page and exact sum; never loads raw QR payloads. */
    @Transaction
    suspend fun overview(filter: HistoryFilter): HistoryOverview {
        val queries = HistoryQueries(filter)
        val counts = counts(queries.counts())
        val rows = summaries(queries.page())
        var amount = BigDecimal.ZERO.setScale(2)
        var cursor: HistoryCursor? = null
        val validAmount = Regex("[0-9]+(\\.[0-9]{1,2})?")
        while (true) {
            val batch = amounts(queries.amounts(cursor))
            if (batch.isEmpty()) break
            for (row in batch) {
                if (!validAmount.matches(row.total)) return HistoryOverview(counts, rows, null)
                amount = amount.add(BigDecimal(row.total))
            }
            val last = batch.last()
            cursor = HistoryCursor(last.scannedAt, last.id)
        }
        return HistoryOverview(counts, rows, amount)
    }
    @Transaction
    suspend fun saveIfNew(scan: ScanEntity): SavedScan {
        val existing = byPayloadKey(scan.payloadKey)
        if (existing != null) return SavedScan(existing, alreadyExists = true)
        val id = insert(scan)
        return if (id != -1L) SavedScan(scan.copy(id = id), alreadyExists = false)
        else SavedScan(requireNotNull(byPayloadKey(scan.payloadKey)), alreadyExists = true)
    }
    @Query("DELETE FROM scans") suspend fun clear()
    @Query("DELETE FROM scans WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM scans WHERE scannedAt < :startOfDay OR scannedAt >= :startOfNextDay")
    suspend fun deleteOutsideDay(startOfDay: Long, startOfNextDay: Long)
    @Query("SELECT MAX(scannedAt) FROM scans") suspend fun latestScanTime(): Long?
    @Transaction
    suspend fun keepLatestDay(zone: ZoneId = ZoneId.systemDefault()) {
        val latest = latestScanTime() ?: return
        val day = Instant.ofEpochMilli(latest).atZone(zone).toLocalDate()
        deleteOutsideDay(day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
}
data class SavedScan(val scan: ScanEntity, val alreadyExists: Boolean)

// Experimental release: old databases may be recreated; no 2 -> 3 migration is required.
@Database(entities = [ScanEntity::class], version = 3, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() { abstract fun scans(): ScanDao }

data class ScannerSettings(val harder: Boolean = true, val denoise: Boolean = false,
    val rotate: Boolean = true, val inverted: Boolean = false, val binarizer: String = "LOCAL_AVERAGE",
    val resolution: String = "HD", val speed: String = "NORMAL", val torch: Boolean = false,
    val tapFocus: Boolean = true, val theme: String = "SYSTEM", val goldenTheme: Boolean = false,
    val scanEngine: ScanEngine = ScanEngine.ZXING) {
    val intervalMs get() = when (speed) { "FAST" -> 100L; "FASTEST" -> 0L; else -> 300L }
}
private val Context.settingsStore by preferencesDataStore("scanner_settings")
class SettingsRepository(private val context: Context) {
    val settings = context.settingsStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        ScannerSettings(p[booleanPreferencesKey("harder")] ?: true, p[booleanPreferencesKey("denoise")] ?: false,
            p[booleanPreferencesKey("rotate")] ?: true, p[booleanPreferencesKey("inverted")] ?: false,
            p[stringPreferencesKey("binarizer")] ?: "LOCAL_AVERAGE", p[stringPreferencesKey("resolution")] ?: "HD",
            p[stringPreferencesKey("speed")] ?: "NORMAL", p[booleanPreferencesKey("torch")] ?: false,
            p[booleanPreferencesKey("tapFocus")] ?: true, p[stringPreferencesKey("theme")] ?: "SYSTEM",
            p[booleanPreferencesKey("goldenTheme")] ?: false,
            ScanEngine.fromPreference(p[stringPreferencesKey("scan_engine")]))
    }
    suspend fun save(s: ScannerSettings) { context.settingsStore.edit { p ->
        mapOf("harder" to s.harder, "denoise" to s.denoise, "rotate" to s.rotate,
            "inverted" to s.inverted, "torch" to s.torch, "tapFocus" to s.tapFocus,
            "goldenTheme" to s.goldenTheme).forEach { (k,v) -> p[booleanPreferencesKey(k)] = v }
        mapOf("binarizer" to s.binarizer, "resolution" to s.resolution, "speed" to s.speed, "theme" to s.theme,
            "scan_engine" to s.scanEngine.name)
            .forEach { (k,v) -> p[stringPreferencesKey(k)] = v }
    } }
}
