package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

interface InvoiceScannerHost : InvoiceBackupActions, InvoiceStatisticsSource {
    fun makeCamera(detection: InvoiceDetection): UIViewController
    fun releaseCamera(controller: UIViewController)
    fun selectPhoto(detection: InvoiceDetection)
    fun shareInvoice(summary: String): Boolean
}

fun invoiceScreenController(host: InvoiceScannerHost, history: InvoiceHistoryStore): UIViewController = ComposeUIViewController {
    InvoiceCheckScreen(
        history = history,
        camera = { detection ->
            UIKitViewController(
                factory = { host.makeCamera(detection) },
                modifier = Modifier.fillMaxSize(),
                onRelease = host::releaseCamera,
            )
        },
        selectPhoto = host::selectPhoto,
        shareInvoice = host::shareInvoice,
        backupActions = host,
        statisticsSource = host,
    )
}
