package com.poetscorner.invoiceqr

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.decoder
import com.poetscorner.invoiceqr.scanner.invoicesInPdf
import com.poetscorner.invoiceqr.ui.ImageImportPhase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class PdfInvoiceImportTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    private fun assetBitmap(name: String): Bitmap =
        instrumentation.context.assets.open(name).use { BitmapFactory.decodeStream(it) }

    private fun invoiceRaw(seller: String): String {
        fun field(tag: Int, value: String): ByteArray {
            val bytes = value.toByteArray(Charsets.UTF_8)
            return byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
        }
        val fields = field(1, seller) + field(2, "310123456789013") +
            field(3, "2026-10-19T12:30:00Z") + field(4, "115.00") + field(5, "15.00")
        return Base64.getEncoder().encodeToString(fields)
    }

    private fun pdfWithThreePages(): File {
        val pdf = File.createTempFile("invoiceqr-pages-", ".pdf", app.cacheDir)
        val unrelated = assetBitmap("unrelated-qr.png")
        val invoice = assetBitmap("valid-invoice-qr.png")
        try {
            val document = PdfDocument()
            try {
                listOf(Color.BLUE to unrelated, Color.GREEN to invoice, Color.RED to invoice)
                    .forEachIndexed { index, (color, image) ->
                        val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                        page.canvas.drawColor(color)
                        page.canvas.drawBitmap(image, null, Rect(40, 40, 300, 300), Paint().apply {
                            isFilterBitmap = false
                        })
                        document.finishPage(page)
                    }
                pdf.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
        } finally {
            unrelated.recycle()
            invoice.recycle()
        }
        return pdf
    }

    private fun pdfWithTwoCodesOnOnePage(): File {
        val pdf = File.createTempFile("invoiceqr-two-codes-", ".pdf", app.cacheDir)
        val unrelated = assetBitmap("unrelated-qr.png")
        val invoice = assetBitmap("valid-invoice-qr.png")
        try {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(700, 500, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(unrelated, null, Rect(30, 30, 280, 280), Paint())
                page.canvas.drawBitmap(invoice, null, Rect(390, 30, 640, 280), Paint())
                document.finishPage(page)
                pdf.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
        } finally {
            unrelated.recycle()
            invoice.recycle()
        }
        return pdf
    }

    private fun pdfWithRepeatedPages(count: Int): File {
        val pdf = File.createTempFile("invoiceqr-long-", ".pdf", app.cacheDir)
        val invoice = assetBitmap("valid-invoice-qr.png")
        try {
            val document = PdfDocument()
            try {
                repeat(count) { index ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(invoice, null, Rect(40, 40, 300, 300), Paint())
                    document.finishPage(page)
                }
                pdf.outputStream().use { document.writeTo(it) }
            } finally { document.close() }
        } finally { invoice.recycle() }
        return pdf
    }

    @Test(timeout = 40_000) fun repeatedInvoiceAcrossPagesAndRenderSizesIsReturnedOnce() = runBlocking {
        val pdf = pdfWithThreePages()
        try {
            val visited = mutableListOf<Int>()
            val pages = mutableListOf<Pair<Int, Int>>()
            val settings = ScannerSettings()
            val result = ScanEngine.ZXING.decoder().use { decoder ->
                invoicesInPdf(app, Uri.fromFile(pdf), onPage = { page, total -> pages += page to total }) { bitmap ->
                    visited += bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    decoder.readAll(bitmap, settings)
                }
            }
            assertEquals(1, result.size)
            assertEquals("Store", result.single().value(1))
            assertEquals("115.00", result.single().value(4))
            assertEquals(6, visited.size)
            assertEquals(2, visited.count { it == Color.RED })
            assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), pages)
        } finally {
            pdf.delete()
        }
    }

    @Test(timeout = 40_000) fun cancellationDuringPdfReadDoesNotReturnPartialInvoices() = runBlocking {
        val pdf = pdfWithThreePages()
        val stagedBefore = app.cacheDir.listFiles()?.count { it.name.startsWith("invoiceqr-import-") } ?: 0
        try {
            var reachedThirdPage = false
            try {
                invoicesInPdf(app, Uri.fromFile(pdf), onPage = { page, _ ->
                    if (page == 3) { reachedThirdPage = true; throw CancellationException("cancel test") }
                }) { listOf(invoiceRaw("First store")) }
                fail("Cancelled PDF returned partial invoices")
            } catch (_: CancellationException) { }
            assertTrue(reachedThirdPage)
            assertEquals(stagedBefore,
                app.cacheDir.listFiles()?.count { it.name.startsWith("invoiceqr-import-") } ?: 0)
        } finally { pdf.delete() }
    }

    @Test(timeout = 40_000) fun findsInvoiceWhenSamePageAlsoContainsUnrelatedQr() = runBlocking {
        val pdf = pdfWithTwoCodesOnOnePage()
        try {
            val settings = ScannerSettings()
            val result = ScanEngine.ZXING.decoder().use { decoder ->
                invoicesInPdf(app, Uri.fromFile(pdf)) { decoder.readAll(it, settings) }
            }
            assertEquals("Store", result.single().value(1))
        } finally {
            pdf.delete()
        }
    }

    @Test(timeout = 40_000) fun collectsDistinctInvoicesAndIgnoresEquivalentQrText() = runBlocking {
        val pdf = pdfWithThreePages()
        try {
            val first = invoiceRaw("First store")
            val second = invoiceRaw("Second store")
            val invoices = invoicesInPdf(app, Uri.fromFile(pdf)) { bitmap ->
                when (bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) {
                    Color.BLUE -> listOf("unrelated QR")
                    Color.GREEN -> listOf(first, first.trimEnd('='), second)
                    else -> listOf(second, first)
                }
            }
            assertEquals(listOf("First store", "Second store"), invoices.map { it.value(1) })
        } finally {
            pdf.delete()
        }
    }

    @Test(timeout = 40_000) fun mixedPdfAndImageImportCountsRepeatedInvoiceOnce() = runBlocking {
        val pdf = pdfWithThreePages()
        val image = File.createTempFile("invoiceqr-image-", ".png", app.cacheDir)
        val store = ViewModelStore()
        try {
            instrumentation.context.assets.open("valid-invoice-qr.png").use { input ->
                image.outputStream().use(input::copyTo)
            }
            val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ScannerViewModel(app, SavedStateHandle()) as T
            })[ScannerViewModel::class.java]
            vm.importInvoiceFiles(listOf(Uri.fromFile(pdf), Uri.fromFile(image)))
            val summary = withTimeout(25_000) { vm.imageImportSummary.filterNotNull().first() }
            assertEquals(1, summary.added + summary.duplicates)
            assertEquals(2, summary.processedFiles)
            assertEquals(2, summary.totalFiles)
            assertEquals(0, summary.invalidQr)
            assertEquals(0, summary.unreadable)
            assertEquals(2, withTimeout(5_000) { vm.screen.first { it == 2 } })
        } finally {
            store.clear()
            pdf.delete()
            image.delete()
        }
    }

    @Test(timeout = 75_000) fun cancellingSecondFileKeepsFirstFileAndSkipsThird() = runBlocking {
        val first = File.createTempFile("invoiceqr-first-", ".png", app.cacheDir)
        val third = File.createTempFile("invoiceqr-third-", ".png", app.cacheDir)
        val pdf = pdfWithRepeatedPages(30)
        val store = ViewModelStore()
        try {
            for (image in listOf(first, third)) {
                instrumentation.context.assets.open("valid-invoice-qr.png").use { input ->
                    image.outputStream().use(input::copyTo)
                }
            }
            val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ScannerViewModel(app, SavedStateHandle()) as T
            })[ScannerViewModel::class.java]
            vm.importInvoiceFiles(listOf(Uri.fromFile(first), Uri.fromFile(pdf), Uri.fromFile(third)))
            val progress = withTimeout(35_000) { vm.imageImport.filterNotNull().first {
                it.current == 2 && it.phase == ImageImportPhase.READING_PAGE
            } }
            assertEquals(30, progress.pageCount)
            vm.cancelFileImport()
            val summary = withTimeout(15_000) { vm.imageImportSummary.filterNotNull().first() }
            assertTrue(summary.cancelled)
            assertTrue(summary.interrupted)
            assertEquals(1, summary.processedFiles)
            assertEquals(3, summary.totalFiles)
            assertEquals(1, summary.added + summary.duplicates)
        } finally {
            store.clear()
            first.delete()
            third.delete()
            pdf.delete()
        }
    }
}
