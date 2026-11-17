package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.writeTransaction
import com.poetscorner.invoiceqr.data.openScanDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.InvoiceCategories
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.data.ScanEntity
import com.poetscorner.invoiceqr.data.StatisticsCategoryCalculation
import com.poetscorner.invoiceqr.data.StatisticsFilterRepository
import com.poetscorner.invoiceqr.data.withCategoryCalculation
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale

/** Explicitly invoked on the isolated validation app; never runs during the normal test suite. */
@RunWith(AndroidJUnit4::class)
class DemoDataSeedTest {
    @Test fun addManyInvoices() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedDemoData") == "true")
        assumeTrue(context.packageName == "com.poetscorner.invoiceqr.categorycalc")

        val sellers = listOf("متجر التجربة الرئيسي", "مكتبة التجربة", "سوق التجربة", "صيدلية التجربة",
            "مطعم التجربة", "أدوات التجربة", "إلكترونيات التجربة", "خدمات التجربة",
            "مخبز التجربة", "شركة التجربة")
        val now = System.currentTimeMillis()
        val rows = (1..720).map { index ->
            val sellerIndex = if (index <= 300) 0 else 1 + (index - 301) % 9
            val date = LocalDate.of(2025, 1, 1).plusDays(((index * 17) % 675).toLong())
            val timestamp = date.atTime(8 + index % 10, index * 7 % 60, index % 60)
                .atOffset(ZoneOffset.ofHours(3)).toString()
            val base = BigDecimal.valueOf((50 + index * 37 % 4950).toLong())
            val tax = base * BigDecimal("0.15")
            val total = base + tax
            val values = listOf(1 to sellers[sellerIndex], 2 to String.format(Locale.ROOT, "3%014d", sellerIndex + 1),
                3 to timestamp, 4 to total.toPlainString(), 5 to tax.toPlainString())
            val payload = values.fold(byteArrayOf()) { bytes, (tag, value) ->
                val encoded = value.toByteArray(Charsets.UTF_8)
                require(encoded.size <= 255)
                bytes + byteArrayOf(tag.toByte(), encoded.size.toByte()) + encoded
            }
            val invoice = ZatcaParser.parse(Base64.getEncoder().encodeToString(payload))
            check(invoice.error == null)
            ScanEntity.from(invoice, now - (720 - index) * 60_000L).copy(
                category = InvoiceCategories.ids[(index - 1) % InvoiceCategories.ids.size],
                invoiceNumber = "DEMO-${index.toString().padStart(4, '0')}",
                note = "بيانات تجريبية")
        }

        val database = openScanDatabase(context)
        try {
            val before = database.scans().count()
            var inserted = 0
            database.writeTransaction {
                rows.forEach { if (database.scans().insert(it) != -1L) inserted++ }
            }
            val after = database.scans().count()
            assertEquals(inserted, after - before)
            assertTrue(after >= 720)
            println("Demo invoices inserted: $inserted; test app total: $after")
        } finally {
            database.close()
        }

        val filters = StatisticsFilterRepository(context)
        val current = filters.load()
        if (current.categoryCalculations.isEmpty()) filters.save(current
            .withCategoryCalculation(1, StatisticsCategoryCalculation.SUBTRACT)
            .withCategoryCalculation(2, StatisticsCategoryCalculation.IGNORE)
            .withCategoryCalculation(5, StatisticsCategoryCalculation.SUBTRACT)
            .withCategoryCalculation(6, StatisticsCategoryCalculation.IGNORE))
    }
}
