package com.poetscorner.invoiceqr.data

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.invoice_category_business_purchases
import com.poetscorner.invoiceqr.resources.invoice_category_needs_review
import com.poetscorner.invoiceqr.resources.invoice_category_none
import com.poetscorner.invoiceqr.resources.invoice_category_other
import com.poetscorner.invoiceqr.resources.invoice_category_personal_purchases
import com.poetscorner.invoiceqr.resources.invoice_category_purchase_return
import com.poetscorner.invoiceqr.resources.invoice_category_sales_invoice
import com.poetscorner.invoiceqr.resources.invoice_category_sales_return
import org.jetbrains.compose.resources.StringResource

fun InvoiceCategories.resource(id: Int): StringResource = when (id) {
    1 -> Res.string.invoice_category_business_purchases
    2 -> Res.string.invoice_category_personal_purchases
    3 -> Res.string.invoice_category_purchase_return
    4 -> Res.string.invoice_category_sales_invoice
    5 -> Res.string.invoice_category_sales_return
    6 -> Res.string.invoice_category_needs_review
    99 -> Res.string.invoice_category_other
    else -> Res.string.invoice_category_none
}
