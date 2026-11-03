package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.R

/** Stable backup IDs. Never renumber an existing category. */
object InvoiceCategories {
    const val NONE = 0
    val ids = 0..5

    fun label(id: Int): Int = when (id) {
        1 -> R.string.invoice_category_work
        2 -> R.string.invoice_category_personal
        3 -> R.string.invoice_category_needs_review
        4 -> R.string.invoice_category_reviewed
        5 -> R.string.invoice_category_other
        else -> R.string.invoice_category_none
    }
}

data class InvoiceAnnotation(val invoiceNumber: String = "", val category: Int = 0,
    val note: String = "") {
    companion object {
        fun from(scan: ScanEntity) = InvoiceAnnotation(scan.invoiceNumber, scan.category, scan.note)
    }
}
