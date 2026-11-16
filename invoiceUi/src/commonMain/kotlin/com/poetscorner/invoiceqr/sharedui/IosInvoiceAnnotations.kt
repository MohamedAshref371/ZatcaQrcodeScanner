package com.poetscorner.invoiceqr.sharedui

import androidx.compose.runtime.Composable

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
    InvoiceAnnotationCard(
        fields = entry.annotation,
        labels = annotationLabels(language),
        categoryLabel = { invoiceCategoryLabel(it, language) },
        editLabel = language.text("تعديل", "Edit"),
        editDescription = language.text("تعديل بيانات الفاتورة", "Edit invoice details"),
        onSave = onSave,
        saveError = language.text("تعذّر حفظ بياناتك.", "Could not save your details."),
    )
}
