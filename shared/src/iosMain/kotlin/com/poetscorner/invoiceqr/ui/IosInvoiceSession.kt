@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.resources.gallery_error
import kotlinx.coroutines.*
import platform.Foundation.*

/** App-lifetime owner. Stage 6's Xcode entry point only needs to host IosScanner(session, ...). */
class IosInvoiceSession {
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
        }), MemoryScannerSavedState(), files, scope)

    /** Copies are app-owned. Keep them until the shared import, including single-photo scanning, finishes. */
    internal fun importInvoices(urls: List<NSURL>) {
        val imports = urls.mapNotNull { url ->
            val directory = NSURL.fileURLWithPath(NSTemporaryDirectory())
                .URLByAppendingPathComponent("invoiceqr-import-${NSUUID().UUIDString}") ?: return@mapNotNull null
            val target = directory.URLByAppendingPathComponent(url.lastPathComponent ?: "invoice-image") ?: return@mapNotNull null
            val manager = NSFileManager.defaultManager
            manager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
            if (manager.copyItemAtURL(url, target, error = null)) target else {
                manager.removeItemAtURL(directory, error = null)
                null
            }
        }
        if (imports.size != urls.size) controller.message.value = com.poetscorner.invoiceqr.ui.text.UiText(
            com.poetscorner.invoiceqr.resources.Res.string.gallery_error)
        val task = controller.importInvoiceFiles(imports.mapNotNull { it.absoluteString })
        if (task == null) imports.forEach(::removeCopy)
        else task.invokeOnCompletion { imports.forEach(::removeCopy) }
    }
    internal fun importBackup(url: NSURL) {
        val task = url.absoluteString?.let(controller::importBackup)
        if (task == null) return
        // asCopy=true from the document picker: URL remains sandbox-local for this operation.
    }
    suspend fun close() { job.cancelAndJoin(); database.close() }
    private fun removeCopy(url: NSURL) {
        url.URLByDeletingLastPathComponent?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
    }
}
