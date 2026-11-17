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

    /** Copies are app-owned. Keep them until the shared import, including single-photo scanning, finishes. */
    internal fun importInvoices(urls: List<NSURL>) {
        val owned = mutableListOf<NSURL>()
        val imports = urls.map { url ->
            copyForImport(url)?.also(owned::add) ?: url
        }
        val task = controller.importInvoiceFiles(imports.mapNotNull { it.absoluteString })
        if (task == null) owned.forEach(::removeCopy)
        else task.invokeOnCompletion { owned.forEach(::removeCopy) }
    }
    internal fun importBackup(url: NSURL) {
        val copy = copyForImport(url)
        val task = (copy ?: url).absoluteString?.let(controller::importBackup)
        if (copy != null) {
            if (task == null) removeCopy(copy)
            else task.invokeOnCompletion { removeCopy(copy) }
        }
    }
    private fun copyForImport(url: NSURL): NSURL? {
        val directory = NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent("invoiceqr-import-${NSUUID().UUIDString}") ?: return null
        val target = directory.URLByAppendingPathComponent(url.lastPathComponent ?: "invoice-image") ?: return null
        val manager = NSFileManager.defaultManager
        manager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
        if (manager.copyItemAtURL(url, target, error = null)) return target
        manager.removeItemAtURL(directory, error = null)
        return null
    }
    suspend fun close() { job.cancelAndJoin(); database.close() }
    private fun removeCopy(url: NSURL) {
        url.URLByDeletingLastPathComponent?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
    }
}
