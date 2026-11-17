package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.app_language
import com.poetscorner.invoiceqr.resources.language_arabic
import com.poetscorner.invoiceqr.resources.language_english
import com.poetscorner.invoiceqr.resources.language_system
import com.poetscorner.invoiceqr.ui.text.stringResource

@Composable
internal fun AppLanguagePicker(selected: String, onChange: (String) -> Unit) {
    Choice(stringResource(Res.string.app_language), selected, listOf(
        "" to stringResource(Res.string.language_system),
        "ar" to stringResource(Res.string.language_arabic),
        "en" to stringResource(Res.string.language_english)
    ), singleLine = true, onChange = onChange)
}
