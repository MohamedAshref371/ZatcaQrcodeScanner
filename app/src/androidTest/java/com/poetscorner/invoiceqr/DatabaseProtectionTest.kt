package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.openScanDatabase
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
        val db = openScanDatabase(context, name)
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
}
