package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceCheck
import com.poetscorner.invoiceqr.shared.InvoiceCheckKind
import com.poetscorner.invoiceqr.shared.ParsedInvoice

private val invoiceFieldNames = listOf(
    "اسم البائع", "الرقم الضريبي", "تاريخ الفاتورة", "إجمالي الفاتورة", "الضريبة",
    "بصمة الفاتورة", "التوقيع الرقمي", "المفتاح العام", "الختم",
)

internal fun invoiceFieldName(tag: Int) = invoiceFieldNames[tag - 1]

internal fun ParsedInvoice.relevantChecks() = checks.filter { phase2 || it.phase == 1 }

internal fun invoiceResultStatus(invoice: ParsedInvoice) =
    if (invoice.passed) "اجتاز فحوص QR المحلية" else "لم يجتز فحوص QR المحلية"

internal fun invoiceCheckLabel(check: InvoiceCheck) = when (check.kind) {
    InvoiceCheckKind.STRUCTURE -> "بنية رمز QR"
    InvoiceCheckKind.PRESENT, InvoiceCheckKind.STAMP -> "وجود ${invoiceFieldName(requireNotNull(check.tag))}"
    InvoiceCheckKind.VAT -> "صيغة الرقم الضريبي"
    InvoiceCheckKind.DATE -> "صيغة تاريخ الفاتورة ووقتها"
    InvoiceCheckKind.TOTAL -> "صيغة إجمالي الفاتورة"
    InvoiceCheckKind.TAX -> "صيغة مبلغ الضريبة"
    InvoiceCheckKind.TAX_TOTAL -> "الضريبة لا تتجاوز الإجمالي"
}

private fun signatureStatus(invoice: ParsedInvoice) = if (invoice.phase2) "لم يُفحص" else "غير موجود"
private const val officialStatus = "لم يُفحص"

internal fun invoiceShareSummary(invoice: ParsedInvoice) = buildString {
    appendLine("ملخص الفاتورة")
    val lastTag = if (invoice.value(6).isNotBlank()) 6 else 5
    for (tag in 1..lastTag) {
        appendLine("${invoiceFieldName(tag)}: ${invoice.value(tag).ifBlank { "غير موجود" }}")
    }
    val phase = if (invoice.phase2) "المرحلة الثانية" else "المرحلة الأولى"
    appendLine("${invoiceResultStatus(invoice)} · $phase")
    appendLine("التحقق من التوقيع: ${signatureStatus(invoice)}")
    append("القبول الرسمي: $officialStatus")
}

@Composable
internal fun InvoiceVerificationDetails(invoice: ParsedInvoice) {
    var levels by remember(invoice.raw) { mutableStateOf(false) }
    var checks by remember(invoice.raw) { mutableStateOf(false) }
    TextButton(onClick = { levels = !levels }, modifier = Modifier.fillMaxWidth().testTag("invoice-levels")) {
        Text(if (levels) "إخفاء مستويات التحقق" else "مستويات التحقق")
    }
    if (levels) {
        InvoiceFieldCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VerificationLevel("بنية رمز QR", localStatus(invoice.error == null))
                VerificationLevel("حقول المرحلة الأولى", localStatus(invoice.checks.filter { it.phase == 1 }.all { it.passed }))
                VerificationLevel("حقول المرحلة الثانية",
                    if (invoice.phase2) localStatus(invoice.checks.filter { it.phase == 2 }.all { it.passed }) else "غير موجودة")
                VerificationLevel("التحقق من التوقيع", signatureStatus(invoice))
                VerificationLevel("القبول الرسمي", officialStatus)
            }
        }
    }
    TextButton(onClick = { checks = !checks }, modifier = Modifier.fillMaxWidth().testTag("invoice-checks")) {
        Text(if (checks) "إخفاء تفاصيل الفحوص" else "تفاصيل الفحوص")
    }
    if (checks) {
        InvoiceFieldCard {
            Column(Modifier.fillMaxWidth().padding(16.dp).testTag("invoice-check-details"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                invoice.relevantChecks().forEach { check ->
                    VerificationLevel(invoiceCheckLabel(check), localStatus(check.passed))
                }
            }
        }
    }
}

private fun localStatus(passed: Boolean) = if (passed) "اجتاز" else "لم يجتز"

@Composable
private fun VerificationLevel(label: String, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun InvoiceRawQr(raw: String) {
    var expanded by remember(raw) { mutableStateOf(false) }
    var copied by remember(raw) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("invoice-raw")) {
        Text(if (expanded) "إخفاء نص QR الأصلي" else "عرض نص QR الأصلي")
    }
    if (expanded) {
        val previewLimit = 4096
        InvoiceFieldCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(raw))
                    copied = true
                }, modifier = Modifier.testTag("invoice-copy-raw")) {
                    Text(if (copied) "تم النسخ" else "نسخ النص الكامل")
                }
                Text(
                    raw.take(previewLimit).toList().joinToString("\u200B"),
                    Modifier.testTag("invoice-raw-text"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                )
                if (raw.length > previewLimit) Text("المعروض أول $previewLimit حرفًا. زر النسخ ينسخ النص الكامل.")
            }
        }
    }
}
