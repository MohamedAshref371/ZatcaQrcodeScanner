package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.R

fun InvoiceCategories.label(id: Int): Int = when (id) {
        1 -> R.string.invoice_category_business_purchases
        2 -> R.string.invoice_category_personal_purchases
        3 -> R.string.invoice_category_purchase_return
        4 -> R.string.invoice_category_sales_invoice
        5 -> R.string.invoice_category_sales_return
        6 -> R.string.invoice_category_needs_review
        99 -> R.string.invoice_category_other
        else -> R.string.invoice_category_none
    }
