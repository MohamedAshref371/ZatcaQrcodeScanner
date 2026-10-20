package com.poetscorner.zatcaqrcodescanner.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

enum class ScanEngine {
    ZXING, ML_KIT;

    companion object {
        fun fromPreference(value: String?): ScanEngine = entries.firstOrNull { it.name == value } ?: ZXING
    }
}

@Entity(tableName = "scans")
data class ScanEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val raw: String,
    val seller: String, val scannedAt: Long = System.currentTimeMillis(), val passed: Boolean, val phase: Int,
    @ColumnInfo(defaultValue = "'STANDARD'") val invoiceType: String = "STANDARD") {
    fun revalidated(): ScanEntity {
        val invoice = ZatcaParser.parse(raw)
        return copy(passed = invoice.passed, phase = if (invoice.phase2) 2 else 1, invoiceType = invoice.type.name)
    }
}
@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY scannedAt DESC, id DESC") fun observe(): Flow<List<ScanEntity>>
    @Query("SELECT * FROM scans ORDER BY scannedAt DESC, id DESC") suspend fun all(): List<ScanEntity>
    @Insert suspend fun insert(scan: ScanEntity)
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
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scans ADD COLUMN invoiceType TEXT NOT NULL DEFAULT 'STANDARD'")
    }
}
@Database(entities = [ScanEntity::class], version = 2, exportSchema = false)
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
