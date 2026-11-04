package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.R

/** Explicit category IDs used in saved invoices and backups. */
object InvoiceCategories {
    const val NONE = 0
    val ids = listOf(0, 1, 2, 3, 4, 5, 6, 99)

    fun label(id: Int): Int = when (id) {
        1 -> R.string.invoice_category_business_purchases
        2 -> R.string.invoice_category_personal_purchases
        3 -> R.string.invoice_category_purchase_return
        4 -> R.string.invoice_category_needs_review
        5 -> R.string.invoice_category_sales_invoice
        6 -> R.string.invoice_category_sales_return
        99 -> R.string.invoice_category_other
        else -> R.string.invoice_category_none
    }
}

data class InvoiceAnnotation(val invoiceNumber: String = "", val category: Int = 0,
    val note: String = "") {
    companion object {
        fun from(scan: ScanEntity) = InvoiceAnnotation(scan.invoiceNumber, scan.category, scan.note)
    }
}
