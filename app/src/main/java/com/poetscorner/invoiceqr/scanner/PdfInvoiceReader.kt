package com.poetscorner.invoiceqr.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

private const val MAX_PDF_BYTES = 64L * 1024 * 1024
private val RENDER_SIZES = intArrayOf(1800, 3200)

/** One invoice per file. A malformed or unrelated QR does not stop the page search. */
internal suspend fun firstInvoiceInPdf(
    context: Context,
    uri: Uri,
    readCodes: (Bitmap) -> List<String>
): Invoice? {
    // Document providers may return a non-seekable descriptor; PdfRenderer requires a file.
    val staged = File.createTempFile("invoiceqr-import-", ".pdf", context.cacheDir)
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to open PDF")
        input.use { source ->
            staged.outputStream().use { target ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_PDF_BYTES) throw IOException("PDF exceeds import limit")
                    target.write(buffer, 0, count)
                }
            }
        }
        val descriptor = ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            PdfRenderer(descriptor).use { renderer ->
                for (pageIndex in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(pageIndex).use { page ->
                        if (page.width <= 0 || page.height <= 0) throw IOException("Invalid PDF page size")
                        for (longEdge in RENDER_SIZES) {
                            currentCoroutineContext().ensureActive()
                            val ratio = longEdge.toDouble() / maxOf(page.width, page.height)
                            val width = (page.width * ratio).roundToInt().coerceAtLeast(1)
                            val height = (page.height * ratio).roundToInt().coerceAtLeast(1)
                            val bitmap = createBitmap(width, height)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                for (raw in readCodes(bitmap)) {
                                    val invoice = ZatcaParser.parse(raw)
                                    if (invoice.error == null) return invoice
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
        return null
    } finally {
        staged.delete()
    }
}
