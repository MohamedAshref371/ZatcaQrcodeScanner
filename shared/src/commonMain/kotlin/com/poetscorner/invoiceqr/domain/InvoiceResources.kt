package com.poetscorner.invoiceqr.domain

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.check_date
import com.poetscorner.invoiceqr.resources.check_present
import com.poetscorner.invoiceqr.resources.check_stamp
import com.poetscorner.invoiceqr.resources.check_structure
import com.poetscorner.invoiceqr.resources.check_tax
import com.poetscorner.invoiceqr.resources.check_tax_total
import com.poetscorner.invoiceqr.resources.check_total
import com.poetscorner.invoiceqr.resources.check_vat
import com.poetscorner.invoiceqr.resources.error_base64
import com.poetscorner.invoiceqr.resources.error_payload
import com.poetscorner.invoiceqr.resources.error_payload_large
import com.poetscorner.invoiceqr.resources.error_tag_bounds
import com.poetscorner.invoiceqr.resources.error_tag_duplicate
import com.poetscorner.invoiceqr.resources.error_tag_unknown
import com.poetscorner.invoiceqr.resources.error_tlv_empty
import com.poetscorner.invoiceqr.resources.error_tlv_header
import com.poetscorner.invoiceqr.resources.error_tlv_length
import com.poetscorner.invoiceqr.resources.error_utf8
import com.poetscorner.invoiceqr.resources.invoice_simplified
import com.poetscorner.invoiceqr.resources.invoice_standard
import com.poetscorner.invoiceqr.resources.tag_date
import com.poetscorner.invoiceqr.resources.tag_hash
import com.poetscorner.invoiceqr.resources.tag_key
import com.poetscorner.invoiceqr.resources.tag_seller
import com.poetscorner.invoiceqr.resources.tag_signature
import com.poetscorner.invoiceqr.resources.tag_stamp
import com.poetscorner.invoiceqr.resources.tag_tax
import com.poetscorner.invoiceqr.resources.tag_total
import com.poetscorner.invoiceqr.resources.tag_vat
import com.poetscorner.invoiceqr.ui.text.UiText
import org.jetbrains.compose.resources.StringResource

internal val InvoiceLabel.resourceId: StringResource get() = when (this) {
    InvoiceLabel.INVOICE_STANDARD -> Res.string.invoice_standard
    InvoiceLabel.INVOICE_SIMPLIFIED -> Res.string.invoice_simplified
    InvoiceLabel.TAG_SELLER -> Res.string.tag_seller
    InvoiceLabel.TAG_VAT -> Res.string.tag_vat
    InvoiceLabel.TAG_DATE -> Res.string.tag_date
    InvoiceLabel.TAG_TOTAL -> Res.string.tag_total
    InvoiceLabel.TAG_TAX -> Res.string.tag_tax
    InvoiceLabel.TAG_HASH -> Res.string.tag_hash
    InvoiceLabel.TAG_SIGNATURE -> Res.string.tag_signature
    InvoiceLabel.TAG_KEY -> Res.string.tag_key
    InvoiceLabel.TAG_STAMP -> Res.string.tag_stamp
    InvoiceLabel.ERROR_PAYLOAD_LARGE -> Res.string.error_payload_large
    InvoiceLabel.ERROR_BASE64 -> Res.string.error_base64
    InvoiceLabel.CHECK_STRUCTURE -> Res.string.check_structure
    InvoiceLabel.CHECK_STAMP -> Res.string.check_stamp
    InvoiceLabel.CHECK_PRESENT -> Res.string.check_present
    InvoiceLabel.CHECK_VAT -> Res.string.check_vat
    InvoiceLabel.CHECK_DATE -> Res.string.check_date
    InvoiceLabel.CHECK_TOTAL -> Res.string.check_total
    InvoiceLabel.CHECK_TAX -> Res.string.check_tax
    InvoiceLabel.CHECK_TAX_TOTAL -> Res.string.check_tax_total
    InvoiceLabel.ERROR_PAYLOAD -> Res.string.error_payload
    InvoiceLabel.ERROR_TLV_EMPTY -> Res.string.error_tlv_empty
    InvoiceLabel.ERROR_TLV_HEADER -> Res.string.error_tlv_header
    InvoiceLabel.ERROR_TAG_UNKNOWN -> Res.string.error_tag_unknown
    InvoiceLabel.ERROR_TAG_DUPLICATE -> Res.string.error_tag_duplicate
    InvoiceLabel.ERROR_TLV_LENGTH -> Res.string.error_tlv_length
    InvoiceLabel.ERROR_TAG_BOUNDS -> Res.string.error_tag_bounds
    InvoiceLabel.ERROR_UTF8 -> Res.string.error_utf8
}

fun InvoiceText.asUiText(): UiText = UiText(label.resourceId, args.map {
    if (it is InvoiceText) it.asUiText() else it
})
