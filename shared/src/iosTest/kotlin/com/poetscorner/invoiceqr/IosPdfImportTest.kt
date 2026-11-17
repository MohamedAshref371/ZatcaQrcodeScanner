@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.IosInvoiceFileReader
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfImportTest {
    @Test fun scansPagesInOrderAndDeduplicatesRepeatedQrCodes() = runTest {
        val path = NSTemporaryDirectory() + "/invoiceqr-${NSUUID().UUIDString}.pdf"
        val url = NSURL.fileURLWithPath(path)
        try {
            val modules = invoiceQrModules.trimIndent().lines()
            val scale = 5.0
            val edge = (modules.size + 8) * scale
            assertTrue(UIGraphicsBeginPDFContextToFile(path, CGRectMake(0.0, 0.0, edge, edge), null))
            try {
                repeat(2) {
                    UIGraphicsBeginPDFPage()
                    UIColor.whiteColor.setFill()
                    UIRectFill(CGRectMake(0.0, 0.0, edge, edge))
                    UIColor.blackColor.setFill()
                    for ((y, row) in modules.withIndex()) for ((x, module) in row.withIndex()) {
                        if (module == '#') UIRectFill(CGRectMake((x + 4) * scale, (y + 4) * scale, scale, scale))
                    }
                }
            } finally {
                UIGraphicsEndPDFContext()
            }
            val pages = mutableListOf<Pair<Int, Int>>()
            val invoices = IosInvoiceFileReader.pdf(url, ScannerSettings()) { page, total -> pages.add(page to total) }
            assertEquals(listOf(1 to 2, 2 to 2), pages)
            assertEquals(INVOICE_QR_PAYLOAD, invoices.single().raw)
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }
}
