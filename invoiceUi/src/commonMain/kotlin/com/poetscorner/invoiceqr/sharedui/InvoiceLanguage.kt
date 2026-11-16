package com.poetscorner.invoiceqr.sharedui

import androidx.compose.runtime.staticCompositionLocalOf

enum class InvoiceLanguage {
    ARABIC, ENGLISH;

    internal fun text(arabic: String, english: String): String =
        if (this == ARABIC) arabic else english
}

enum class InvoiceLanguagePreference {
    DEVICE, ARABIC, ENGLISH;

    internal fun resolve(deviceLanguage: InvoiceLanguage): InvoiceLanguage = when (this) {
        DEVICE -> deviceLanguage
        ARABIC -> InvoiceLanguage.ARABIC
        ENGLISH -> InvoiceLanguage.ENGLISH
    }
}

interface InvoiceLanguageSettings {
    fun languagePreference(): InvoiceLanguagePreference
    fun deviceLanguage(): InvoiceLanguage
    fun saveLanguage(preference: InvoiceLanguagePreference)
}

internal val LocalInvoiceLanguage = staticCompositionLocalOf { InvoiceLanguage.ARABIC }
