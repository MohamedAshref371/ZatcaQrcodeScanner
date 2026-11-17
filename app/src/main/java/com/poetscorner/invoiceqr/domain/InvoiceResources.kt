package com.poetscorner.invoiceqr.domain

import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.ui.text.UiText

internal val InvoiceLabel.resourceId: Int get() = when (this) {
    InvoiceLabel.INVOICE_STANDARD -> R.string.invoice_standard
    InvoiceLabel.INVOICE_SIMPLIFIED -> R.string.invoice_simplified
    InvoiceLabel.TAG_SELLER -> R.string.tag_seller
    InvoiceLabel.TAG_VAT -> R.string.tag_vat
    InvoiceLabel.TAG_DATE -> R.string.tag_date
    InvoiceLabel.TAG_TOTAL -> R.string.tag_total
    InvoiceLabel.TAG_TAX -> R.string.tag_tax
    InvoiceLabel.TAG_HASH -> R.string.tag_hash
    InvoiceLabel.TAG_SIGNATURE -> R.string.tag_signature
    InvoiceLabel.TAG_KEY -> R.string.tag_key
    InvoiceLabel.TAG_STAMP -> R.string.tag_stamp
    InvoiceLabel.ERROR_PAYLOAD_LARGE -> R.string.error_payload_large
    InvoiceLabel.ERROR_BASE64 -> R.string.error_base64
    InvoiceLabel.CHECK_STRUCTURE -> R.string.check_structure
    InvoiceLabel.CHECK_STAMP -> R.string.check_stamp
    InvoiceLabel.CHECK_PRESENT -> R.string.check_present
    InvoiceLabel.CHECK_VAT -> R.string.check_vat
    InvoiceLabel.CHECK_DATE -> R.string.check_date
    InvoiceLabel.CHECK_TOTAL -> R.string.check_total
    InvoiceLabel.CHECK_TAX -> R.string.check_tax
    InvoiceLabel.CHECK_TAX_TOTAL -> R.string.check_tax_total
    InvoiceLabel.ERROR_PAYLOAD -> R.string.error_payload
    InvoiceLabel.ERROR_TLV_EMPTY -> R.string.error_tlv_empty
    InvoiceLabel.ERROR_TLV_HEADER -> R.string.error_tlv_header
    InvoiceLabel.ERROR_TAG_UNKNOWN -> R.string.error_tag_unknown
    InvoiceLabel.ERROR_TAG_DUPLICATE -> R.string.error_tag_duplicate
    InvoiceLabel.ERROR_TLV_LENGTH -> R.string.error_tlv_length
    InvoiceLabel.ERROR_TAG_BOUNDS -> R.string.error_tag_bounds
    InvoiceLabel.ERROR_UTF8 -> R.string.error_utf8
}

internal fun InvoiceText.asUiText(): UiText = UiText(label.resourceId, args.map {
    if (it is InvoiceText) it.asUiText() else it
})
