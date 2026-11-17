package com.poetscorner.invoiceqr

import android.content.ContentValues
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanEntity
import com.poetscorner.invoiceqr.data.openScanDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceAnnotationMigrationTest {
    @Test fun opensVersionOneAndAddsEmptyAnnotations() = runBlocking {
        verifyUpgrade(version = 1)
    }

    @Test fun opensVersionTwoAndRechecksDatesAcrossMigrationPages() = runBlocking {
        verifyUpgrade(version = 2)
    }

    @Test fun opensInstalledVersionThreeWithoutRewritingInvoices() = runBlocking {
        verifyUpgrade(version = 3)
    }

    private suspend fun verifyUpgrade(version: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "room-kmp-upgrade-$version.db"
        val schemas = "com.poetscorner.invoiceqr.data.ScanDatabase"
        val schema = instrumentation.context.assets.open("$schemas/$version.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        val count = if (version == 2) 502 else 2
        val original = (1..count).map { index ->
            val date = if (index == count) "2026-10-20T10:00:00+03:00" else "2026-10-20T10:00:00"
            val row = historyFixture(index, date = date)
            row.copy(id = index.toLong(), passed = index == count, invoiceNumber = "INV-$index",
                category = 2, note = "ملاحظة $index", details = row.details.copy(invoiceDay = 123L))
        }

        context.deleteDatabase(name)
        try {
            // Seed the exact released schema using Android's previous SQLite implementation.
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
                val entity = schema.getJSONArray("entities").getJSONObject(0)
                fun sql(owner: JSONObject) = owner.getString("createSql").replace("\${TABLE_NAME}", "scans")
                legacy.execSQL(sql(entity))
                val indices = entity.getJSONArray("indices")
                repeat(indices.length()) { legacy.execSQL(sql(indices.getJSONObject(it))) }
                val setup = schema.getJSONArray("setupQueries")
                repeat(setup.length()) { legacy.execSQL(setup.getString(it)) }
                legacy.beginTransaction()
                try {
                    for (row in original) legacy.insertOrThrow("scans", null, row.legacyValues(version))
                    legacy.version = version
                    legacy.setTransactionSuccessful()
                } finally {
                    legacy.endTransaction()
                }
            }

            val upgraded = openScanDatabase(context, name)
            try {
                val dao = upgraded.scans()
                val expected = original.map { row ->
                    val annotated = if (version == 1) row.copy(invoiceNumber = "", category = 0, note = "") else row
                    if (version == 3) annotated else {
                        val refreshed = historyFixture(row.id.toInt(), date = row.details.invoiceDate)
                        annotated.copy(passed = refreshed.passed,
                            details = annotated.details.copy(invoiceDay = refreshed.details.invoiceDay))
                    }
                }
                assertEquals(expected, dao.all().sortedBy { it.id })
                val last = expected.last()
                assertTrue(dao.saveIfNew(last.copy(id = 0)).alreadyExists)
                assertEquals(count, dao.count())
                val newId = dao.insert(historyFixture(99999))
                assertTrue(newId > last.id)
            } finally {
                upgraded.close()
            }

            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { verified ->
                assertEquals(3, verified.version)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun ScanEntity.legacyValues(version: Int) = ContentValues().apply {
        put("id", id)
        put("raw", raw)
        put("seller", seller)
        put("scannedAt", scannedAt)
        put("passed", passed)
        put("phase", phase)
        put("invoiceType", invoiceType)
        put("payloadKey", payloadKey)
        put("sellerSearch", details.sellerSearch)
        put("vat", details.vat)
        put("invoiceDate", details.invoiceDate)
        put("invoiceDay", details.invoiceDay)
        put("total", details.total)
        put("tax", details.tax)
        put("hash", details.hash)
        put("hashPresent", details.hashPresent)
        if (version >= 2) {
            put("invoiceNumber", invoiceNumber)
            put("category", category)
            put("note", note)
        }
    }
}
