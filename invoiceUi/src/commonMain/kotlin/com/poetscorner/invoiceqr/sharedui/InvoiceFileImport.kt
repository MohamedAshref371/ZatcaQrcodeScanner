package com.poetscorner.invoiceqr.sharedui

data class InvoiceFilesResult(
    val added: Int,
    val duplicates: Int,
    val needsReview: Int,
    val invalidQr: Int,
    val unreadable: Int,
    val saveFailed: Int,
    val processedFiles: Int,
    val totalFiles: Int,
    val cancelled: Boolean,
)

interface InvoiceFilesObserver {
    fun progress(file: Int, total: Int, page: Int, pages: Int)
    fun complete(result: InvoiceFilesResult)
}
