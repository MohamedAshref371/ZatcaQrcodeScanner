package com.poetscorner.invoiceqr.data

import androidx.room.*
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.migration.Migration
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

@Entity(tableName = "scans", indices = [Index(value = ["payloadKey"], unique = true),
    Index(value = ["scannedAt", "id"]), Index(value = ["invoiceDay", "scannedAt", "id"])])
data class ScanEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val raw: String,
    val seller: String, val scannedAt: Long = Clock.System.now().toEpochMilliseconds(), val passed: Boolean, val phase: Int,
    @ColumnInfo(defaultValue = "'STANDARD'") val invoiceType: String = "STANDARD",
    val payloadKey: String = invoicePayloadKey(raw),
    @ColumnInfo(defaultValue = "''") val invoiceNumber: String = "",
    @ColumnInfo(defaultValue = "0") val category: Int = 0,
    @ColumnInfo(defaultValue = "''") val note: String = "",
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
    @Query("SELECT id, raw, scannedAt, invoiceNumber, category, note FROM scans WHERE id > :afterId ORDER BY id LIMIT 50")
    suspend fun backupRows(afterId: Long): List<BackupRow>
    @Query("SELECT * FROM scans ORDER BY scannedAt DESC, id DESC") suspend fun all(): List<ScanEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(scan: ScanEntity): Long
    @Insert suspend fun insertBatch(scans: List<ScanEntity>)
    @Query("SELECT * FROM scans WHERE payloadKey = :key LIMIT 1") suspend fun byPayloadKey(key: String): ScanEntity?
    @Query("SELECT * FROM scans WHERE id = :id") suspend fun byId(id: Long): ScanEntity?
    @Query("UPDATE scans SET invoiceNumber = :number, category = :category, note = :note WHERE id = :id")
    suspend fun updateAnnotation(id: Long, number: String, category: Int, note: String): Int
    @RawQuery(observedEntities = [ScanEntity::class]) fun observeCounts(query: RoomRawQuery): Flow<HistoryCounts>
    @RawQuery suspend fun summaries(query: RoomRawQuery): List<HistorySummary>
    @RawQuery suspend fun exportRows(query: RoomRawQuery): List<ExportInvoice>
    @RawQuery suspend fun statisticsRows(query: RoomRawQuery): List<StatisticsInvoice>
    @RawQuery suspend fun hasHash(query: RoomRawQuery): Boolean
    @RawQuery suspend fun counts(query: RoomRawQuery): HistoryCounts
    @RawQuery suspend fun amounts(query: RoomRawQuery): List<HistoryAmount>
    /** One snapshot for the count, loaded card range and exact sum; never loads raw QR payloads. */
    @Transaction
    suspend fun overview(filter: HistoryFilter, rowLimit: Int = 50): HistoryOverview =
        readHistoryOverview(filter, rowLimit,
            count = { counts(it.roomQuery()) },
            summaries = { summaries(it.roomQuery()) },
            amounts = { amounts(it.roomQuery()) })
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
    @Query("SELECT COUNT(*) FROM scans WHERE scannedAt < :cutoff") suspend fun countBeforeScan(cutoff: Long): Int
    @Query("SELECT COUNT(*) FROM scans WHERE invoiceDay < :cutoff") suspend fun countBeforeInvoice(cutoff: Long): Int
    @Query("DELETE FROM scans WHERE scannedAt < :cutoff") suspend fun deleteBeforeScan(cutoff: Long)
    @Query("DELETE FROM scans WHERE invoiceDay < :cutoff") suspend fun deleteBeforeInvoice(cutoff: Long)
    suspend fun deletionCount(plan: HistoryDeletionPlan): Int = when {
        plan.period == HistoryDeletionPeriod.ALL -> count()
        plan.source == HistoryDateSource.SCAN -> countBeforeScan(requireNotNull(plan.cutoffScanTime))
        else -> countBeforeInvoice(requireNotNull(plan.cutoffDay))
    }
    suspend fun deleteSelection(plan: HistoryDeletionPlan) {
        when {
            plan.period == HistoryDeletionPeriod.ALL -> clear()
            plan.source == HistoryDateSource.SCAN -> deleteBeforeScan(requireNotNull(plan.cutoffScanTime))
            else -> deleteBeforeInvoice(requireNotNull(plan.cutoffDay))
        }
    }
}
data class SavedScan(val scan: ScanEntity, val alreadyExists: Boolean)

@Database(entities = [ScanEntity::class], version = 3, exportSchema = true)
@ConstructedBy(ScanDatabaseConstructor::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scans(): ScanDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE scans ADD COLUMN invoiceNumber TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE scans ADD COLUMN category INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE scans ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                var lastId = 0L
                connection.prepare("SELECT id, raw FROM scans WHERE id > ? ORDER BY id LIMIT 500").use { select ->
                    connection.prepare("UPDATE scans SET passed = ?, invoiceDay = ? WHERE id = ?").use { update ->
                        while (true) {
                            select.bindLong(1, lastId)
                            val rows = buildList {
                                while (select.step()) add(select.getLong(0) to select.getText(1))
                            }
                            select.reset()
                            if (rows.isEmpty()) break
                            for ((id, raw) in rows) {
                                val invoice = ZatcaParser.parse(raw)
                                val day = runCatching {
                                    ZatcaParser.parseTimestamp(invoice.value(3)).local.date.toEpochDays()
                                }.getOrNull()
                                update.bindLong(1, if (invoice.passed) 1 else 0)
                                if (day == null) update.bindNull(2) else update.bindLong(2, day)
                                update.bindLong(3, id)
                                update.step()
                                update.reset()
                            }
                            lastId = rows.last().first
                        }
                    }
                }
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object ScanDatabaseConstructor : RoomDatabaseConstructor<ScanDatabase> {
    override fun initialize(): ScanDatabase
}

const val INVOICE_DATABASE_NAME = "invoices.db"

internal fun RoomDatabase.Builder<ScanDatabase>.buildInvoiceDatabase(): ScanDatabase =
    addMigrations(ScanDatabase.MIGRATION_1_2, ScanDatabase.MIGRATION_2_3)
        .setDriver(BundledSQLiteDriver())
        .build()

suspend fun <T> ScanDatabase.readSnapshot(block: suspend () -> T): T =
    useReaderConnection { connection -> connection.deferredTransaction { block() } }

suspend fun <T> ScanDatabase.writeTransaction(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
