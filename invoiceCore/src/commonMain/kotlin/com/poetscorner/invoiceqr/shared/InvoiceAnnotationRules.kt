package com.poetscorner.invoiceqr.shared

object InvoiceAnnotationRules {
    const val MAX_NUMBER = 120
    const val MAX_NOTE = 2_000
    const val NO_CATEGORY = 0
    val categoryIds = listOf(NO_CATEGORY, 1, 2, 3, 4, 5, 6, 99)

    fun isValid(number: String, category: Int, note: String): Boolean =
        number.length <= MAX_NUMBER && category in categoryIds && note.length <= MAX_NOTE
}
