@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.data.*
import kotlinx.coroutines.*
import platform.Foundation.*

/** App-lifetime owner. Stage 6's Xcode entry point only needs to host IosScanner(session, ...). */
class IosInvoiceSession(savedState: ScannerSavedState = MemoryScannerSavedState()) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private val database = openIosScanDatabase()
    internal val files = IosScannerFiles()
    val controller = ScannerController(database, iosSettingsRepository(), iosReviewRepository(),
        StatisticsFilterStore(object : StringPreferences {
            private val preferences = NSUserDefaults.standardUserDefaults
            private fun name(key: String) = "statistics_filters.$key"
            override fun contains(key: String) = preferences.objectForKey(name(key)) != null
            override fun get(key: String, fallback: String?) = preferences.stringForKey(name(key)) ?: fallback
            override fun write(change: StringPreferencesWriter.() -> Unit) {
                change(StringPreferencesWriter { key, value ->
                    if (value == null) preferences.removeObjectForKey(name(key))
                    else preferences.setObject(value, forKey = name(key))
                })
            }
        }), savedState, files, scope)

    /** UIDocumentPicker(asCopy=true) already supplies sandbox copies. PHPicker copies are handed off here. */
    internal fun importInvoices(urls: List<NSURL>) {
        val owned = urls.filter { url ->
            url.path?.startsWith(NSTemporaryDirectory()) == true &&
                url.URLByDeletingLastPathComponent?.lastPathComponent?.startsWith("invoiceqr-photo-") == true
        }
        val task = controller.importInvoiceFiles(urls.mapNotNull { it.absoluteString })
        if (task == null) owned.forEach(::removeCopy)
        else task.invokeOnCompletion { owned.forEach(::removeCopy) }
    }
    internal fun importBackup(url: NSURL) {
        // The system picker completes its copy before this callback and before the shared transaction.
        url.absoluteString?.let(controller::importBackup)
    }
    suspend fun close() { job.cancelAndJoin(); database.close() }
    private fun removeCopy(url: NSURL) {
        url.URLByDeletingLastPathComponent?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
    }
}
