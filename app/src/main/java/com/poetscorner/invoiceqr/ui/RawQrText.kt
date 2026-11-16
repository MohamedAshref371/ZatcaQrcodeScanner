package com.poetscorner.invoiceqr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.poetscorner.invoiceqr.sharedui.InvoiceQrCharacterWrap
import com.poetscorner.invoiceqr.sharedui.InvoiceRawQrText

internal val QrCharacterWrap = InvoiceQrCharacterWrap

@Composable
internal fun RawQrText(raw: String, modifier: Modifier = Modifier) = InvoiceRawQrText(raw, modifier)
