package com.poetscorner.invoiceqr.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Keep resource references in state, so a locale change also updates existing results and messages. */
data class UiText(@param:StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    fun resolve(context: Context): String = context.getString(id, *args.map {
        if (it is UiText) it.resolve(context) else it
    }.toTypedArray())
}

@Composable
fun UiText.localized(): String = stringResource(id, *args.map {
    if (it is UiText) it.localized() else it
}.toTypedArray())
