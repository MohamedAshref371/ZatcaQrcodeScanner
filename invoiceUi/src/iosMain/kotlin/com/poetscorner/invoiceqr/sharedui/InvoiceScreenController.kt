package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

interface InvoiceScannerHost {
    fun makeCamera(detection: InvoiceDetection): UIViewController
    fun releaseCamera(controller: UIViewController)
    fun selectPhoto(detection: InvoiceDetection)
}

fun invoiceScreenController(host: InvoiceScannerHost): UIViewController = ComposeUIViewController {
    InvoiceCheckScreen(
        camera = { detection ->
            UIKitViewController(
                factory = { host.makeCamera(detection) },
                modifier = Modifier.fillMaxSize(),
                onRelease = host::releaseCamera,
            )
        },
        selectPhoto = host::selectPhoto,
    )
}
