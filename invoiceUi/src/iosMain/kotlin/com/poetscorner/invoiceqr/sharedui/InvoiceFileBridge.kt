package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser
import com.poetscorner.invoiceqr.shared.normalizedInvoicePayload

/** The native document reader uses the same invoice parser and payload normalization as the UI. */
class InvoiceImportCandidate(val raw: String) {
    private val invoice = ZatcaInvoiceParser.parse(raw)
    val valid: Boolean get() = invoice.error == null
    val passed: Boolean get() = invoice.passed
    val normalizedPayload: String get() = if (valid) normalizedInvoicePayload(raw) else ""
    val seller: String get() = invoice.value(1)
    val vat: String get() = invoice.value(2)
    val total: String get() = invoice.value(4)
    val tax: String get() = invoice.value(5)
    val invoiceDay: String get() = if (valid) invoiceHistoryDay(invoice) else ""
}
