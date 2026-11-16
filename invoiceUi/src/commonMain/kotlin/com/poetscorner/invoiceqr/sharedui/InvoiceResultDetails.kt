package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceCheck
import com.poetscorner.invoiceqr.shared.InvoiceCheckKind
import com.poetscorner.invoiceqr.shared.ParsedInvoice

private val arabicInvoiceFieldNames = listOf(
    "اسم البائع", "الرقم الضريبي", "تاريخ الفاتورة", "إجمالي الفاتورة", "الضريبة",
    "بصمة الفاتورة", "التوقيع الرقمي", "المفتاح العام", "الختم",
)

private val englishInvoiceFieldNames = listOf(
    "Seller name", "VAT number", "Invoice date", "Invoice total", "VAT amount",
    "Invoice hash", "Digital signature", "Public key", "Stamp",
)

internal fun invoiceFieldName(tag: Int, language: InvoiceLanguage): String =
    (if (language == InvoiceLanguage.ARABIC) arabicInvoiceFieldNames else englishInvoiceFieldNames)[tag - 1]

internal fun ParsedInvoice.relevantChecks() = checks.filter { phase2 || it.phase == 1 }

internal fun invoiceResultStatus(invoice: ParsedInvoice, language: InvoiceLanguage) =
    if (invoice.passed) language.text("اجتاز فحوص QR المحلية", "Passed local QR checks") else language.text("لم يجتز فحوص QR المحلية", "Failed local QR checks")

internal fun invoiceCheckLabel(check: InvoiceCheck, language: InvoiceLanguage) = when (check.kind) {
    InvoiceCheckKind.STRUCTURE -> language.text("بنية رمز QR", "QR structure")
    InvoiceCheckKind.PRESENT, InvoiceCheckKind.STAMP -> language.text("وجود ${invoiceFieldName(requireNotNull(check.tag), language)}", "Required field: ${invoiceFieldName(requireNotNull(check.tag), language)}")
    InvoiceCheckKind.VAT -> language.text("صيغة الرقم الضريبي", "VAT number format")
    InvoiceCheckKind.DATE -> language.text("صيغة تاريخ الفاتورة ووقتها", "Invoice date and time format")
    InvoiceCheckKind.TOTAL -> language.text("صيغة إجمالي الفاتورة", "Invoice total format")
    InvoiceCheckKind.TAX -> language.text("صيغة مبلغ الضريبة", "VAT amount format")
    InvoiceCheckKind.TAX_TOTAL -> language.text("الضريبة لا تتجاوز الإجمالي", "VAT does not exceed the total")
}

private fun signatureStatus(invoice: ParsedInvoice, language: InvoiceLanguage) = if (invoice.phase2) language.text("لم يُفحص", "Not checked") else language.text("غير موجود", "Missing")
private fun officialStatus(language: InvoiceLanguage) = language.text("لم يُفحص", "Not checked")

internal fun invoiceShareSummary(invoice: ParsedInvoice, language: InvoiceLanguage) = buildString {
    appendLine(language.text("ملخص الفاتورة", "Invoice summary"))
    val lastTag = if (invoice.value(6).isNotBlank()) 6 else 5
    for (tag in 1..lastTag) {
        val value = invoice.value(tag).ifBlank { language.text("غير موجود", "Missing") }
        appendLine("${invoiceFieldName(tag, language)}: $value")
    }
    val phase = if (invoice.phase2) language.text("المرحلة الثانية", "Phase two") else language.text("المرحلة الأولى", "Phase one")
    appendLine("${invoiceResultStatus(invoice, language)} · $phase")
    appendLine(language.text("التحقق من التوقيع: ${signatureStatus(invoice, language)}", "Signature verification: ${signatureStatus(invoice, language)}"))
    append(language.text("القبول الرسمي: ${officialStatus(language)}", "Official acceptance: ${officialStatus(language)}"))
}

internal fun invoiceVerificationRows(invoice: ParsedInvoice, language: InvoiceLanguage): List<InvoiceVerificationRow> {
    val core = invoice.error == null && invoice.checks.filter { it.phase == 1 }.all { it.passed }
    val phaseTwo = invoice.error == null && invoice.checks.filter { it.phase == 2 }.all { it.passed }
    val passed = language.text("اجتاز", "Passed")
    val failed = language.text("لم يجتز", "Failed")
    return listOf(
        InvoiceVerificationRow(language.text("بنية رمز QR", "QR structure"),
            if (invoice.error == null) passed else failed, invoice.error != null),
        InvoiceVerificationRow(language.text("حقول المرحلة الأولى", "Phase one fields"), if (core) passed else failed, !core),
        InvoiceVerificationRow(language.text("حقول المرحلة الثانية", "Phase two fields"),
            if (!invoice.phase2) language.text("غير موجودة", "Missing") else if (phaseTwo) passed else failed,
            invoice.phase2 && !phaseTwo),
        InvoiceVerificationRow(language.text("التحقق من التوقيع", "Signature verification"), signatureStatus(invoice, language)),
        InvoiceVerificationRow(language.text("القبول الرسمي", "Official acceptance"), officialStatus(language)),
    )
}

@Suppress("DEPRECATION")
@Composable
internal fun InvoiceRawQrContent(raw: String) {
    val language = LocalInvoiceLanguage.current
    var copied by remember(raw) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val previewLimit = 4096
    InvoiceFieldCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(raw))
                copied = true
            }, modifier = Modifier.testTag("invoice-copy-raw")) {
                Text(if (copied) language.text("تم النسخ", "Copied") else language.text("نسخ النص الكامل", "Copy full text"))
            }
            InvoiceRawQrText(raw.take(previewLimit), Modifier.fillMaxWidth().testTag("invoice-raw-text"))
            if (raw.length > previewLimit) Text(language.text(
                "المعروض أول $previewLimit حرفًا. زر النسخ ينسخ النص الكامل.",
                "Showing the first $previewLimit characters. Copy includes the full text."))
        }
    }
}
