package com.poetscorner.invoiceqr

import android.app.Application

class InvoiceQrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TemporaryInvoiceFiles.cleanAbandoned(cacheDir)
    }
}
