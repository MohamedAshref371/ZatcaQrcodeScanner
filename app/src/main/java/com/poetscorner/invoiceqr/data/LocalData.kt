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
    @Query("SELECT DISTINCT trim(seller) AS name FROM scans WHERE :term != '' AND instr(sellerSearch, :term) > 0 AND trim(seller) != '' ORDER BY name COLLATE NOCASE LIMIT 5")
    suspend fun sellerSuggestions(term: String): List<String>
    @Query("SELECT COUNT(*) FROM scans") suspend fun count(): Int
    @Query("SELECT id, raw, scannedAt FROM scans WHERE id > :afterId ORDER BY id LIMIT 50")
    suspend fun backupRows(afterId: Long): List<BackupRow>
    @Query("SELECT * FROM scans ORDER BY scannedAt DESC, id DESC") suspend fun all(): List<ScanEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(scan: ScanEntity): Long
    @Insert suspend fun insertBatch(scans: List<ScanEntity>)
    @Query("SELECT * FROM scans WHERE payloadKey = :key LIMIT 1") suspend fun byPayloadKey(key: String): ScanEntity?
    @Query("SELECT * FROM scans WHERE id = :id") suspend fun byId(id: Long): ScanEntity?
    @RawQuery(observedEntities = [ScanEntity::class]) fun observeCounts(query: SupportSQLiteQuery): Flow<HistoryCounts>
    @RawQuery suspend fun summaries(query: SupportSQLiteQuery): List<HistorySummary>
    @RawQuery suspend fun exportRows(query: SupportSQLiteQuery): List<ExportInvoice>
    @RawQuery suspend fun statisticsRows(query: SupportSQLiteQuery): List<StatisticsInvoice>
    @RawQuery suspend fun hasHash(query: SupportSQLiteQuery): Boolean
    @RawQuery suspend fun counts(query: SupportSQLiteQuery): HistoryCounts
    @RawQuery suspend fun amounts(query: SupportSQLiteQuery): List<HistoryAmount>
    /** One snapshot for the count, loaded card range and exact sum; never loads raw QR payloads. */
    @Transaction
    suspend fun overview(filter: HistoryFilter, rowLimit: Int = 50): HistoryOverview {
        require(rowLimit > 0)
        val queries = HistoryQueries(filter)
        val counts = counts(queries.counts())
        val rows = mutableListOf<HistorySummary>()
        while (rows.size < minOf(rowLimit, counts.matching)) {
            val last = rows.lastOrNull()?.let { HistoryCursor(it.scannedAt, it.id) }
            val page = summaries(queries.page(last, minOf(500, rowLimit - rows.size)))
            if (page.isEmpty()) break
            rows.addAll(page)
        }
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

// Version 3 is the first preserved schema. Future changes must include a tested migration.
@Database(entities = [ScanEntity::class], version = 3, exportSchema = true)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scans(): ScanDao

    companion object {
        fun open(context: Context, name: String = "invoices.db"): ScanDatabase =
            Room.databaseBuilder(context.applicationContext, ScanDatabase::class.java, name).build()
    }
}

data class ScannerSettings(val harder: Boolean = true, val denoise: Boolean = false,
    val rotate: Boolean = true, val inverted: Boolean = false, val binarizer: String = "LOCAL_AVERAGE",
    val resolution: String = "HD", val speed: String = "NORMAL", val torch: Boolean = false,
    val theme: String = "SYSTEM", val goldenTheme: Boolean = false,
    val scanEngine: ScanEngine = ScanEngine.ZXING, val cropEnabled: Boolean = true,
    val supporterTheme: String = if (goldenTheme) "GOLD" else "DEFAULT") {
    val intervalMs get() = when (speed) { "FAST" -> 100L; "FASTEST" -> 0L; else -> 300L }
}
private val Context.settingsStore by preferencesDataStore("scanner_settings")
class SettingsRepository(private val context: Context) {
    val settings = context.settingsStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        ScannerSettings(p[booleanPreferencesKey("harder")] ?: true, p[booleanPreferencesKey("denoise")] ?: false,
            p[booleanPreferencesKey("rotate")] ?: true, p[booleanPreferencesKey("inverted")] ?: false,
            p[stringPreferencesKey("binarizer")] ?: "LOCAL_AVERAGE", p[stringPreferencesKey("resolution")] ?: "HD",
            p[stringPreferencesKey("speed")] ?: "NORMAL", p[booleanPreferencesKey("torch")] ?: false,
            p[stringPreferencesKey("theme")] ?: "SYSTEM",
            p[booleanPreferencesKey("goldenTheme")] ?: false,
            ScanEngine.fromPreference(p[stringPreferencesKey("scan_engine")]),
            p[booleanPreferencesKey("crop_enabled")] ?: true,
            p[stringPreferencesKey("supporter_theme")] ?: if (p[booleanPreferencesKey("goldenTheme")] == true) "GOLD" else "DEFAULT")
    }
    suspend fun save(s: ScannerSettings) { context.settingsStore.edit { p ->
        mapOf("harder" to s.harder, "denoise" to s.denoise, "rotate" to s.rotate,
            "inverted" to s.inverted, "torch" to s.torch,
            "goldenTheme" to s.goldenTheme, "crop_enabled" to s.cropEnabled)
            .forEach { (k,v) -> p[booleanPreferencesKey(k)] = v }
        mapOf("binarizer" to s.binarizer, "resolution" to s.resolution, "speed" to s.speed, "theme" to s.theme,
            "scan_engine" to s.scanEngine.name, "supporter_theme" to s.supporterTheme)
            .forEach { (k,v) -> p[stringPreferencesKey(k)] = v }
    } }
}
