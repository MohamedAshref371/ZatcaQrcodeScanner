@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.scanner

import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.invoicePayloadKey
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.domain.ZatcaParser
import kotlinx.cinterop.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.CoreGraphics.*
import platform.Foundation.NSURL
import platform.PDFKit.*
import platform.UIKit.*
import kotlin.math.roundToInt

private const val MAX_PDF_BYTES = 64L * 1024 * 1024
private val RENDER_SIZES = intArrayOf(1800, 3200)

/** Reads local copies supplied by the iOS document or photo picker. */
object IosInvoiceFileReader {
    suspend fun image(url: NSURL, settings: ScannerSettings): List<Invoice> {
        currentCoroutineContext().ensureActive()
        val source = requireNotNull(UIImage.imageWithContentsOfFile(requireNotNull(url.path))) { "Invalid image file" }
        val luma = source.toLuma(maxEdge = 4096)
        val options = settings.qrReadOptions()
        val raw = IosZxingQrReader.readImportedImage(luma, options) ?: return emptyList()
        return listOf(ZatcaParser.parse(raw))
    }

    suspend fun pdf(url: NSURL, settings: ScannerSettings, onPage: (Int, Int) -> Unit): List<Invoice> {
        val path = Path(requireNotNull(url.path))
        val bytes = requireNotNull(SystemFileSystem.metadataOrNull(path)?.size) { "Unable to open PDF" }
        require(bytes in 1..MAX_PDF_BYTES) { "PDF exceeds import limit or is empty" }
        val pdf = PDFDocument(uRL = url)
        require(pdf.pageCount > 0uL && !pdf.isLocked()) { "Invalid PDF document" }
        val invoices = linkedMapOf<String, Invoice>()
        val options = settings.qrReadOptions(maxSymbols = 0xff)
        val pageCount = pdf.pageCount.toInt()
        for (index in 0 until pageCount) {
            currentCoroutineContext().ensureActive()
            onPage(index + 1, pageCount)
            val page = requireNotNull(pdf.pageAtIndex(index.toULong()))
            for (edge in RENDER_SIZES) {
                currentCoroutineContext().ensureActive()
                val luma = autoreleasepool {
                    val bounds = page.boundsForBox(kPDFDisplayBoxMediaBox)
                    val size = bounds.useContents { this.size.width to this.size.height }
                    require(size.first > 0 && size.second > 0) { "Invalid PDF page size" }
                    val factor = edge / maxOf(size.first, size.second)
                    val thumbnail = page.thumbnailOfSize(CGSizeMake(size.first * factor, size.second * factor),
                        forBox = kPDFDisplayBoxMediaBox)
                    thumbnail.toLuma(edge)
                }
                for (result in IosZxingQrReader.read(luma, options)) {
                    val text = result.text?.takeIf { result.error == null } ?: continue
                    val invoice = ZatcaParser.parse(text)
                    if (invoice.error == null) {
                        val key = invoicePayloadKey(text)
                        if (key !in invoices) invoices[key] = invoice
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return invoices.values.toList()
    }
}

private fun UIImage.toLuma(maxEdge: Int): LumaImage = autoreleasepool {
    val (sourceWidth, sourceHeight) = size.useContents { width to height }
    require(sourceWidth > 0.0 && sourceHeight > 0.0) { "Invalid image size" }
    val factor = minOf(scale, maxEdge / maxOf(sourceWidth, sourceHeight))
    val width = (sourceWidth * factor).roundToInt().coerceAtLeast(1)
    val height = (sourceHeight * factor).roundToInt().coerceAtLeast(1)
    val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), false, 1.0)
    val upright = try {
        // UIKit applies the original orientation, including EXIF mirroring, during this draw.
        drawInRect(rect)
        requireNotNull(UIGraphicsGetImageFromCurrentImageContext())
    } finally {
        UIGraphicsEndImageContext()
    }
    val image = requireNotNull(upright.CGImage)
    val luma = ByteArray(width * height) { 255.toByte() }
    luma.usePinned { array ->
        val gray = CGColorSpaceCreateDeviceGray()
        try {
            val context = requireNotNull(CGBitmapContextCreate(array.addressOf(0), width.toULong(), height.toULong(),
                8uL, width.toULong(), gray, kCGImageAlphaNone.value))
            try {
                CGContextDrawImage(context, rect, image)
            } finally {
                CGContextRelease(context)
            }
        } finally {
            CGColorSpaceRelease(gray)
        }
    }
    LumaImage(luma, width, height)
}
