package com.poetscorner.invoiceqr

import android.content.ContentValues
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class InvoiceAnnotationMigrationTest {
    @Test fun versionOneInvoiceSurvivesUpgradeWithEmptyUserDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "annotation_migration_test.db"
        context.deleteDatabase(name)
        val original = historyFixture(99001)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, raw TEXT NOT NULL, seller TEXT NOT NULL, scannedAt INTEGER NOT NULL, passed INTEGER NOT NULL, phase INTEGER NOT NULL, invoiceType TEXT NOT NULL DEFAULT 'STANDARD', payloadKey TEXT NOT NULL, sellerSearch TEXT NOT NULL, vat TEXT NOT NULL, invoiceDate TEXT NOT NULL, invoiceDay INTEGER, total TEXT NOT NULL, tax TEXT NOT NULL, hash TEXT NOT NULL, hashPresent INTEGER NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_scans_payloadKey ON scans(payloadKey)")
            old.execSQL("CREATE INDEX index_scans_scannedAt_id ON scans(scannedAt, id)")
            old.execSQL("CREATE INDEX index_scans_invoiceDay_scannedAt_id ON scans(invoiceDay, scannedAt, id)")
            val values = ContentValues().apply {
                put("raw", original.raw); put("seller", original.seller)
                put("scannedAt", original.scannedAt); put("passed", if (original.passed) 1 else 0)
                put("phase", original.phase); put("invoiceType", original.invoiceType)
                put("payloadKey", original.payloadKey); put("sellerSearch", original.details.sellerSearch)
                put("vat", original.details.vat); put("invoiceDate", original.details.invoiceDate)
                original.details.invoiceDay?.let { put("invoiceDay", it) }
                put("total", original.details.total); put("tax", original.details.tax)
                put("hash", original.details.hash); put("hashPresent", if (original.details.hashPresent) 1 else 0)
            }
            assertNotNull(old.insertOrThrow("scans", null, values))
            old.version = 1
        }
        try {
            val upgraded = ScanDatabase.open(context, name)
            try {
                val scan = requireNotNull(upgraded.scans().byPayloadKey(original.payloadKey))
                assertEquals(original.raw, scan.raw)
                assertEquals(original.scannedAt, scan.scannedAt)
                assertEquals("", scan.invoiceNumber)
                assertEquals(0, scan.category)
                assertEquals("", scan.note)
                assertEquals(1, upgraded.scans().updateAnnotation(scan.id, "INV-001", 1, "Work"))
                assertEquals("INV-001", upgraded.scans().byId(scan.id)?.invoiceNumber)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun versionTwoRechecksStoredTimestampWithoutChangingItsRawText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "timestamp_migration_test.db"
        context.deleteDatabase(name)
        val rawDate = "2026-10-20T10:00:00+03:00"
        val original = historyFixture(99002, date = rawDate).copy(passed = true,
            details = historyFixture(99002, date = rawDate).details.copy(
                invoiceDay = LocalDate.of(2026, 10, 20).toEpochDay()))
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, raw TEXT NOT NULL, seller TEXT NOT NULL, scannedAt INTEGER NOT NULL, passed INTEGER NOT NULL, phase INTEGER NOT NULL, invoiceType TEXT NOT NULL DEFAULT 'STANDARD', payloadKey TEXT NOT NULL, invoiceNumber TEXT NOT NULL DEFAULT '', category INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', sellerSearch TEXT NOT NULL, vat TEXT NOT NULL, invoiceDate TEXT NOT NULL, invoiceDay INTEGER, total TEXT NOT NULL, tax TEXT NOT NULL, hash TEXT NOT NULL, hashPresent INTEGER NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_scans_payloadKey ON scans(payloadKey)")
            old.execSQL("CREATE INDEX index_scans_scannedAt_id ON scans(scannedAt, id)")
            old.execSQL("CREATE INDEX index_scans_invoiceDay_scannedAt_id ON scans(invoiceDay, scannedAt, id)")
            val values = ContentValues().apply {
                put("raw", original.raw); put("seller", original.seller)
                put("scannedAt", original.scannedAt); put("passed", 1)
                put("phase", original.phase); put("invoiceType", original.invoiceType)
                put("payloadKey", original.payloadKey); put("invoiceNumber", "INV-002")
                put("category", 2); put("note", "Keep this")
                put("sellerSearch", original.details.sellerSearch); put("vat", original.details.vat)
                put("invoiceDate", rawDate); put("invoiceDay", original.details.invoiceDay)
                put("total", original.details.total); put("tax", original.details.tax)
                put("hash", original.details.hash); put("hashPresent", if (original.details.hashPresent) 1 else 0)
            }
            old.insertOrThrow("scans", null, values)
            old.version = 2
        }
        try {
            val upgraded = ScanDatabase.open(context, name)
            try {
                val scan = requireNotNull(upgraded.scans().byPayloadKey(original.payloadKey))
                assertFalse(scan.passed)
                assertNull(scan.details.invoiceDay)
                assertEquals(rawDate, scan.details.invoiceDate)
                assertEquals(original.raw, scan.raw)
                assertEquals("INV-002", scan.invoiceNumber)
                assertEquals(2, scan.category)
                assertEquals("Keep this", scan.note)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
