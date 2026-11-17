package com.poetscorner.invoiceqr.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.poetscorner.invoiceqr.ui.formatInteger
import com.poetscorner.invoiceqr.ui.uiLocaleTag
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource as composeStringResource

/** Retain resource references so that a language change relocalizes existing results and messages. */
data class UiText(val id: StringResource, val args: List<Any> = emptyList()) {
    constructor(id: StringResource, vararg args: Any) : this(id, args.toList())
}

@Composable
fun UiText.localized(): String = stringResource(id, *args.toTypedArray())

@Composable
internal fun stringResource(resource: StringResource, vararg args: Any): String {
    val locale = uiLocaleTag()
    if (args.isEmpty()) return key(locale) { composeStringResource(resource) }
    val resolved = args.map {
        when (it) {
            is UiText -> it.localized()
            is Int -> formatInteger(it.toLong(), locale, grouped = false)
            is Long -> formatInteger(it, locale, grouped = false)
            else -> it
        }
    }.toTypedArray()
    return key(locale) { composeStringResource(resource, *resolved) }
}

suspend fun UiText.resolve(environment: ResourceEnvironment, locale: String): String {
    if (args.isEmpty()) return getString(environment, id)
    return getString(environment, id, *args.map {
        when (it) {
            is UiText -> it.resolve(environment, locale)
            is Int -> formatInteger(it.toLong(), locale, grouped = false)
            is Long -> formatInteger(it, locale, grouped = false)
            else -> it
        }
    }.toTypedArray())
}
