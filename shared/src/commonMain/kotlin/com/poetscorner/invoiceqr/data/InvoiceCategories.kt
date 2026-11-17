package com.poetscorner.invoiceqr.data

/** Explicit category IDs used in saved invoices and backups. */
object InvoiceCategories {
    const val NONE = 0
    val ids = listOf(0, 1, 2, 3, 4, 5, 6, 99)
}

data class InvoiceAnnotation(val invoiceNumber: String = "", val category: Int = 0, val note: String = "") {
    companion object
}

fun InvoiceAnnotation.Companion.from(scan: ScanEntity) =
    InvoiceAnnotation(scan.invoiceNumber, scan.category, scan.note)
