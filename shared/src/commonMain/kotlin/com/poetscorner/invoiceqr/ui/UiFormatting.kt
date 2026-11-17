package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDateTime

@Composable
internal expect fun uiLocaleTag(): String

internal expect fun formatInteger(value: Long, locale: String, grouped: Boolean = true): String
internal expect fun formatDecimal(value: String, locale: String): String
internal expect fun formatDay(epochDay: Long, locale: String, short: Boolean = false): String
internal expect fun formatMonth(epochDay: Long, locale: String): String
internal expect fun formatCheckedAt(millis: Long, locale: String): String
internal expect fun formatInvoiceDateTime(value: LocalDateTime, locale: String): Pair<String, String>
