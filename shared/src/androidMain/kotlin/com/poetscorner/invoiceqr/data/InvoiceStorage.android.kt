package com.poetscorner.invoiceqr.data

import android.content.Context
import androidx.room.Room
import okio.Path
import okio.Path.Companion.toPath

fun openScanDatabase(context: Context, name: String = INVOICE_DATABASE_NAME): ScanDatabase {
    val app = context.applicationContext
    return Room.databaseBuilder<ScanDatabase>(app, app.getDatabasePath(name).absolutePath)
        .buildInvoiceDatabase()
}

fun inMemoryScanDatabase(context: Context): ScanDatabase =
    Room.inMemoryDatabaseBuilder<ScanDatabase>(context.applicationContext).buildInvoiceDatabase()

private val stores = mutableMapOf<Path, InvoicePreferenceStores>()

private fun preferenceStores(context: Context): InvoicePreferenceStores {
    val directory = context.applicationContext.filesDir.resolve("datastore").absolutePath.toPath()
    return synchronized(stores) { stores.getOrPut(directory) { InvoicePreferenceStores(directory) } }
}

fun SettingsRepository(context: Context) = SettingsRepository(preferenceStores(context).settings)
fun ReviewRepository(context: Context) = ReviewRepository(preferenceStores(context).review)
