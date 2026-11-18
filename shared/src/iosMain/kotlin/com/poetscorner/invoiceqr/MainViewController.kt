@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.poetscorner.invoiceqr

import androidx.compose.ui.window.ComposeUIViewController
import com.poetscorner.invoiceqr.ui.IosInvoiceSession
import com.poetscorner.invoiceqr.ui.IosScanner
import com.poetscorner.invoiceqr.ui.ScannerController
import kotlin.io.encoding.Base64
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/** Xcode entry point: the SwiftUI host calls this and embeds the returned controller. */
fun MainViewController(version: String, privacyPolicyUrl: String, storeUrl: String?): UIViewController {
    val session = IosInvoiceSession()
    var host: UIViewController? = null
    val controller = ComposeUIViewController {
        IosScanner(session, { host }, version, privacyPolicyUrl, storeUrl, {}, {})
    }
    host = controller
    // Only set by the screenshot UI test (`-seedDemoData en|ar`); never in a normal launch.
    NSUserDefaults.standardUserDefaults.stringForKey("seedDemoData")?.let { seedDemoInvoices(session.controller, it) }
    return controller
}

private fun seedDemoInvoices(controller: ScannerController, language: String) {
    val sellers = if (language == "ar") listOf("متجر تجريبي", "مقهى تجريبي", "مكتبة تجريبية")
        else listOf("Sample Store", "Sample Cafe", "Sample Bookshop")
    val totals = listOf("230.00", "57.50", "115.00")
    val vats = listOf("30.00", "7.50", "15.00")
    MainScope().launch {
        controller.update { it.copy(theme = "LIGHT") }
        for (index in (0..5).reversed()) {
            val fields = listOf(1 to sellers[index % 3], 2 to "300000000000003",
                3 to "2026-10-${20 - index}T10:30:00", 4 to totals[index % 3], 5 to vats[index % 3])
            val bytes = fields.fold(ByteArray(0)) { all, (tag, text) ->
                val data = text.encodeToByteArray()
                all + byteArrayOf(tag.toByte(), data.size.toByte()) + data
            }
            controller.scanned(Base64.encode(bytes))
        }
    }
}
