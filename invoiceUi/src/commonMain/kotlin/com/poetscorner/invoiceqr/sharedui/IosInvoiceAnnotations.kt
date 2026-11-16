package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private fun annotationLabels(language: InvoiceLanguage) = InvoiceAnnotationLabels(language.text("بياناتك", "Your details"), language.text("رقم الفاتورة", "Invoice number"), language.text("التصنيف", "Category"), language.text("ملاحظة", "Note"), language.text("حفظ", "Save"), language.text("إلغاء", "Cancel"))

internal fun invoiceCategoryLabel(id: Int, language: InvoiceLanguage): String = when (id) {
    1 -> language.text("مشتريات عمل", "Business purchase")
    2 -> language.text("مشتريات شخصية", "Personal purchase")
    3 -> language.text("مرتجع مشتريات", "Purchase return")
    4 -> language.text("فاتورة مبيعات", "Sales invoice")
    5 -> language.text("مرتجع مبيعات", "Sales return")
    6 -> language.text("تحتاج مراجعة", "Needs review")
    99 -> language.text("أخرى", "Other")
    else -> language.text("بدون تصنيف", "Uncategorized")
}

@Composable
internal fun IosInvoiceAnnotations(entry: InvoiceHistoryEntry, onSave: (InvoiceAnnotationFields) -> Boolean) {
    val language = LocalInvoiceLanguage.current
    val annotationLabels = annotationLabels(language)
    var editing by remember(entry.id) { mutableStateOf(false) }
    val fields = entry.annotation
    InvoiceFieldCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(annotationLabels.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { editing = true }, modifier = Modifier.testTag("invoice-annotation-edit")) {
                    Text(language.text("تعديل", "Edit"))
                }
            }
            if (fields.number.isNotBlank()) {
                Text("${annotationLabels.number}: ${fields.number}", Modifier.testTag("invoice-saved-number"))
            }
            if (fields.category != 0) {
                Text(invoiceCategoryLabel(fields.category, language), Modifier.testTag("invoice-saved-category"))
            }
            if (fields.note.isNotBlank()) {
                Text(fields.note, Modifier.testTag("invoice-saved-note"))
            }
        }
    }
    if (editing) InvoiceAnnotationDialog(
        initial = fields,
        labels = annotationLabels,
        categoryLabel = { invoiceCategoryLabel(it, language) },
        onDismiss = { editing = false },
        onSave = onSave,
        saveError = language.text("تعذّر حفظ بياناتك.", "Could not save your details."),
    )
}
