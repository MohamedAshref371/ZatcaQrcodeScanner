@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import platform.Foundation.NSURL
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject

/** Presents native actions from the scanner's hosting view controller. Call on the main thread. */
class IosSystemActions(private val host: () -> UIViewController?) {
    // UIKit delegates are weak, so hold the active picker until it finishes.
    private val photos = IosPhotoPicker()
    private var activePicker: UIViewController? = null
    private var documentDelegate: UIDocumentPickerDelegateProtocol? = null
    var filePickerOpen by mutableStateOf(false)
        private set

    fun pickInvoiceFiles(onSelection: (List<NSURL>) -> Unit): Boolean =
        openDocuments(listOf(UTTypeImage, UTTypePDF), allowMultiple = true, onSelection)

    /** UIKit supplies the two system sources behind the existing shared import action. */
    fun pickInvoiceSource(photosLabel: String, filesLabel: String, cancelLabel: String,
        onSelection: (List<NSURL>) -> Unit): Boolean {
        val presenter = host()?.takeIf { it.presentedViewController == null && !filePickerOpen } ?: return false
        val sheet = UIAlertController.alertControllerWithTitle(null, null, UIAlertControllerStyleActionSheet)
        fun choose(action: () -> Unit) {
            sheet.dismissViewControllerAnimated(true) {
                filePickerOpen = false
                activePicker = null
                action()
            }
        }
        sheet.addAction(UIAlertAction.actionWithTitle(photosLabel, UIAlertActionStyleDefault) {
            choose {
                filePickerOpen = true
                if (!photos.show(presenter) { urls ->
                    filePickerOpen = false
                    // Keep failed provider items in the batch so shared import reports them as unreadable.
                    val copies = urls.filterNotNull()
                    onSelection(urls.map { it ?: NSURL.fileURLWithPath(platform.Foundation.NSTemporaryDirectory() +
                        "invoiceqr-missing-${platform.Foundation.NSUUID().UUIDString}.jpg") })
                    copies.forEach { url -> url.URLByDeletingLastPathComponent?.let {
                        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(it, error = null)
                    } }
                }) filePickerOpen = false
            }
        })
        sheet.addAction(UIAlertAction.actionWithTitle(filesLabel, UIAlertActionStyleDefault) { choose { pickInvoiceFiles(onSelection) } })
        sheet.addAction(UIAlertAction.actionWithTitle(cancelLabel, UIAlertActionStyleCancel) {
            filePickerOpen = false
            activePicker = null
        })
        sheet.popoverPresentationController?.let { it.sourceView = presenter.view; it.sourceRect = presenter.view.bounds }
        filePickerOpen = true
        activePicker = sheet
        presenter.presentViewController(sheet, animated = true, completion = null)
        return true
    }

    fun cancelPicker() {
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
            activePicker?.dismissViewControllerAnimated(true, completion = null)
            activePicker = null
            documentDelegate = null
            filePickerOpen = false
        }
    }

    fun pickBackup(onSelection: (List<NSURL>) -> Unit): Boolean =
        openDocuments(listOf(UTTypeJSON, UTTypePlainText, UTTypeData), allowMultiple = false, onSelection)

    private fun openDocuments(types: List<UTType>, allowMultiple: Boolean,
        onSelection: (List<NSURL>) -> Unit): Boolean {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.allowsMultipleSelection = allowMultiple
        return presentPicker(picker, onSelection)
    }

    /** URL belongs to the app. The caller can remove it after this callback. */
    fun exportDocument(url: NSURL, onFinished: (Boolean) -> Unit): Boolean {
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        return presentPicker(picker) { onFinished(it.isNotEmpty()) }
    }

    private fun presentPicker(picker: UIDocumentPickerViewController, onSelection: (List<NSURL>) -> Unit): Boolean {
        val presenter = host()?.takeIf { it.presentedViewController == null && !filePickerOpen } ?: return false
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                finish(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = finish(emptyList())

            private fun finish(urls: List<NSURL>) {
                activePicker = null
                documentDelegate = null
                filePickerOpen = false
                onSelection(urls)
            }
        }
        activePicker = picker
        documentDelegate = delegate
        picker.delegate = delegate
        filePickerOpen = true
        presenter.presentViewController(picker, animated = true, completion = null)
        return true
    }

    fun share(text: String): Boolean {
        val presenter = host()?.takeIf { it.presentedViewController == null } ?: return false
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        sheet.popoverPresentationController?.let {
            it.sourceView = presenter.view
            it.sourceRect = presenter.view.bounds
        }
        presenter.presentViewController(sheet, animated = true, completion = null)
        return true
    }

    fun copy(value: String) {
        if (value.isNotBlank()) UIPasteboard.generalPasteboard.string = value
    }

    fun openLink(value: String): Boolean {
        val url = NSURL.URLWithString(value) ?: return false
        val application = UIApplication.sharedApplication
        if (!application.canOpenURL(url)) return false
        application.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }

    fun requestReview(stillAllowed: () -> Boolean) {
        val scene = host()?.view?.window?.windowScene ?: return
        if (stillAllowed()) SKStoreReviewController.requestReviewInScene(scene)
    }
}
