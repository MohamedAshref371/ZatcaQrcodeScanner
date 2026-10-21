package com.poetscorner.invoiceqr

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseProtectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun <T> withDatabase(name: String, block: suspend (ScanDatabase) -> T): T {
        val db = ScanDatabase.open(context, name)
        try { return block(db) } finally { db.close() }
    }

    @Test fun reopeningTheDatabasePreservesTheInvoice() = runBlocking {
        val name = "history-protection-${UUID.randomUUID()}.db"
        val fixture = historyFixture(93001, seller = "Preserved invoice", hash = false)
        try {
            val id = withDatabase(name) { it.scans().saveIfNew(fixture).scan.id }
            withDatabase(name) { db ->
                val row = requireNotNull(db.scans().byId(id))
                assertEquals(fixture.raw, row.raw)
                assertEquals(fixture.scannedAt, row.scannedAt)
                assertTrue(db.scans().saveIfNew(fixture).alreadyExists)
                assertEquals(1, db.scans().all().size)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun missingMigrationFailsWithoutDeletingHistory() = runBlocking {
        val name = "history-protection-${UUID.randomUUID()}.db"
        val fixture = historyFixture(93002, seller = "Must survive missing migration", hash = false)
        try {
            withDatabase(name) { it.scans().saveIfNew(fixture) }
            val path = context.getDatabasePath(name).absolutePath
            // Simulate an older schema version without registering a migration to version 3.
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 2 }
            withDatabase(name) { db ->
                try {
                    db.scans().all()
                    fail("A missing migration must never recreate the database")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
                }
            }
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                sqlite.rawQuery("SELECT raw, scannedAt FROM scans", null).use { cursor ->
                    assertEquals(1, cursor.count)
                    assertTrue(cursor.moveToFirst())
                    assertEquals(fixture.raw, cursor.getString(0))
                    assertEquals(fixture.scannedAt, cursor.getLong(1))
                }
                assertEquals(2, sqlite.version)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
