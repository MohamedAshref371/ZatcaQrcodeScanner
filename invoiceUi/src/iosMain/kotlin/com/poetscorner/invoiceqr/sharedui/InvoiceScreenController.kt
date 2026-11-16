package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import platform.UIKit.UIViewController

interface InvoiceScannerHost : InvoiceBackupActions, InvoiceStatisticsSource, InvoiceCsvActions, InvoiceLanguageSettings {
    fun makeCamera(detection: InvoiceDetection): UIViewController
    fun releaseCamera(controller: UIViewController)
    fun selectPhoto(detection: InvoiceDetection)
    fun importFiles(observer: InvoiceFilesObserver)
    fun importSharedFiles(observer: InvoiceFilesObserver): Boolean
    fun cancelFileImport()
    fun shareInvoice(summary: String): Boolean
}

class InvoiceSharedImportRequests {
    internal val revision = MutableStateFlow(0)

    fun request() {
        revision.update { it + 1 }
    }
}

fun invoiceScreenController(
    host: InvoiceScannerHost,
    history: InvoiceHistoryStore,
    sharedImports: InvoiceSharedImportRequests,
): UIViewController = ComposeUIViewController {
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
        importFiles = host::importFiles,
        importSharedFiles = host::importSharedFiles,
        sharedImportRequests = sharedImports.revision,
        cancelFileImport = host::cancelFileImport,
        shareInvoice = host::shareInvoice,
        backupActions = host,
        statisticsSource = host,
        csvActions = host,
        languageSettings = host,
    )
}
