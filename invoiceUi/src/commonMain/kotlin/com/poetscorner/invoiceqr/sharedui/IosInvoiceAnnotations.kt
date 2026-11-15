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

private val annotationLabels = InvoiceAnnotationLabels("بياناتك", "رقم الفاتورة", "التصنيف", "ملاحظة", "حفظ", "إلغاء")

internal fun arabicInvoiceCategory(id: Int): String = when (id) {
    1 -> "مشتريات عمل"
    2 -> "مشتريات شخصية"
    3 -> "مرتجع مشتريات"
    4 -> "فاتورة مبيعات"
    5 -> "مرتجع مبيعات"
    6 -> "تحتاج مراجعة"
    99 -> "أخرى"
    else -> "بدون تصنيف"
}

@Composable
internal fun IosInvoiceAnnotations(entry: InvoiceHistoryEntry, onSave: (InvoiceAnnotationFields) -> Boolean) {
    var editing by remember(entry.id) { mutableStateOf(false) }
    val fields = entry.annotation
    InvoiceFieldCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(annotationLabels.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { editing = true }, modifier = Modifier.testTag("invoice-annotation-edit")) {
                    Text("تعديل")
                }
            }
            if (fields.number.isNotBlank()) {
                Text("${annotationLabels.number}: ${fields.number}", Modifier.testTag("invoice-saved-number"))
            }
            if (fields.category != 0) {
                Text(arabicInvoiceCategory(fields.category), Modifier.testTag("invoice-saved-category"))
            }
            if (fields.note.isNotBlank()) {
                Text(fields.note, Modifier.testTag("invoice-saved-note"))
            }
        }
    }
    if (editing) InvoiceAnnotationDialog(
        initial = fields,
        labels = annotationLabels,
        categoryLabel = { arabicInvoiceCategory(it) },
        onDismiss = { editing = false },
        onSave = onSave,
        saveError = "تعذّر حفظ بياناتك.",
    )
}
