@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.ui

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.*
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.*

/** Photo picker URLs are copied before Apple's item-provider callback returns. */
class IosPhotoPicker {
    private var delegate: PHPickerViewControllerDelegateProtocol? = null

    fun show(presenter: UIViewController, onSelection: (List<NSURL?>) -> Unit): Boolean {
        if (delegate != null || presenter.presentedViewController != null) return false
        val picker = PHPickerViewController(PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 0
        })
        val observer = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                val photos = didFinishPicking.filterIsInstance<PHPickerResult>()
                picker.dismissViewControllerAnimated(true) {
                    if (photos.isEmpty()) {
                        delegate = null
                        onSelection(emptyList())
                    } else {
                        copyPhotos(photos) { urls ->
                            delegate = null
                            onSelection(urls)
                        }
                    }
                }
            }
        }
        delegate = observer
        picker.delegate = observer
        presenter.presentViewController(picker, animated = true, completion = null)
        return true
    }

    private fun copyPhotos(photos: List<PHPickerResult>, onSelection: (List<NSURL?>) -> Unit) {
        val copies = MutableList<NSURL?>(photos.size) { null }
        var remaining = photos.size
        photos.forEachIndexed { index, result ->
            result.itemProvider.loadFileRepresentationForTypeIdentifier(UTTypeImage.identifier) { source, _ ->
                val copied = source?.let(::temporaryCopy)
                dispatch_async(dispatch_get_main_queue()) {
                    copies[index] = copied
                    if (--remaining == 0) onSelection(copies)
                }
            }
        }
    }

    private fun temporaryCopy(source: NSURL): NSURL? {
        val directory = NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent("invoiceqr-photo-${NSUUID().UUIDString}") ?: return null
        val file = directory.URLByAppendingPathComponent(source.lastPathComponent ?: "invoice-image") ?: return null
        val manager = NSFileManager.defaultManager
        if (!manager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
            return null
        }
        if (manager.copyItemAtURL(source, file, error = null)) return file
        manager.removeItemAtURL(directory, error = null)
        return null
    }
}
