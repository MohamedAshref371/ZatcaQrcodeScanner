package com.poetscorner.invoiceqr.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.poetscorner.invoiceqr.R

@Composable
internal fun AppLanguagePicker() {
    // Read the explicit preference, not the resolved locale: an empty list follows the device.
    val configuration = LocalConfiguration.current
    var selected by remember(configuration) {
        mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        selected = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
    }
    Choice(stringResource(R.string.app_language), selected, listOf(
        "" to stringResource(R.string.language_system),
        "ar" to stringResource(R.string.language_arabic),
        "en" to stringResource(R.string.language_english)
    )) { language ->
        // Switching to the device language may keep the same resolved locale and skip recreation.
        selected = language
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }
}
