package com.poetscorner.invoiceqr.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private val invoiceDirectory by lazy {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    requireNotNull(directory?.path).toPath()
}

private val preferences by lazy { InvoicePreferenceStores(invoiceDirectory / "datastore") }

fun openIosScanDatabase(): ScanDatabase =
    Room.databaseBuilder<ScanDatabase>((invoiceDirectory / INVOICE_DATABASE_NAME).toString())
        .buildInvoiceDatabase()

fun iosSettingsRepository() = SettingsRepository(preferences.settings)
fun iosReviewRepository() = ReviewRepository(preferences.review)
