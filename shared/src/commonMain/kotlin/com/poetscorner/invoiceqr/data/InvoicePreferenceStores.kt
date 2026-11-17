package com.poetscorner.invoiceqr.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path

internal const val SCANNER_SETTINGS_FILE = "scanner_settings.preferences_pb"
internal const val REVIEW_USAGE_FILE = "review_usage.preferences_pb"

internal class InvoicePreferenceStores(directory: Path) {
    val settings by lazy { PreferenceDataStoreFactory.createWithPath { directory / SCANNER_SETTINGS_FILE } }
    val review by lazy { PreferenceDataStoreFactory.createWithPath { directory / REVIEW_USAGE_FILE } }
}
