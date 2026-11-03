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
import com.poetscorner.invoiceqr.scanner.firstInvoiceInPdf
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfInvoiceImportTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    private fun assetBitmap(name: String): Bitmap =
        instrumentation.context.assets.open(name).use { BitmapFactory.decodeStream(it) }

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

    @Test(timeout = 40_000) fun skipsUnrelatedQrAndStopsAfterFirstInvoicePage() = runBlocking {
        val pdf = pdfWithThreePages()
        try {
            val visited = mutableListOf<Int>()
            val settings = ScannerSettings()
            val result = ScanEngine.ZXING.decoder().use { decoder ->
                firstInvoiceInPdf(app, Uri.fromFile(pdf)) { bitmap ->
                    visited += bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    decoder.readAll(bitmap, settings)
                }
            }
            assertNotNull(result)
            assertEquals("Store", result!!.value(1))
            assertEquals("115.00", result.value(4))
            assertEquals(3, visited.size)
            assertFalse(visited.contains(Color.RED))
        } finally {
            pdf.delete()
        }
    }

    @Test(timeout = 40_000) fun findsInvoiceWhenSamePageAlsoContainsUnrelatedQr() = runBlocking {
        val pdf = pdfWithTwoCodesOnOnePage()
        try {
            val settings = ScannerSettings()
            val result = ScanEngine.ZXING.decoder().use { decoder ->
                firstInvoiceInPdf(app, Uri.fromFile(pdf)) { decoder.readAll(it, settings) }
            }
            assertEquals("Store", result?.value(1))
        } finally {
            pdf.delete()
        }
    }

    @Test(timeout = 40_000) fun mixedPdfAndImageImportCountsEachFileOnce() = runBlocking {
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
            assertEquals(2, summary.added + summary.duplicates)
            assertEquals(0, summary.invalidQr)
            assertEquals(0, summary.unreadable)
            assertEquals(2, withTimeout(5_000) { vm.screen.first { it == 2 } })
        } finally {
            store.clear()
            pdf.delete()
            image.delete()
        }
    }
}
