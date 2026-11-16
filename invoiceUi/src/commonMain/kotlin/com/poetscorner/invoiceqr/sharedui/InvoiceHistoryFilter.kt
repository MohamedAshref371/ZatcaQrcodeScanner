package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceAnnotationRules
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

internal enum class HistoryDateSource {
    INVOICE, CHECK;

    fun label(language: InvoiceLanguage) = when (this) {
        INVOICE -> language.text("تاريخ الفاتورة", "Invoice date")
        CHECK -> language.text("تاريخ الفحص", "Check date")
    }
}

internal data class InvoiceHistoryFilter(
    val term: String = "",
    val dateSource: HistoryDateSource = HistoryDateSource.INVOICE,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val category: Int? = null,
    val annotationTerm: String = "",
) {
    private val hasDates get() = from != null || to != null
    val isActive get() = term.isNotBlank() || hasDates || category != null || annotationTerm.isNotBlank()

    fun summary(language: InvoiceLanguage): String {
        val labels = listOfNotNull(
            dateSummary(language).takeIf { hasDates },
            category?.let { invoiceCategoryLabel(it, language) },
            annotationTerm.takeIf { it.isNotBlank() },
        )
        return labels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            ?: language.text("تصفية السجل", "Filter history")
    }

    fun dateSummary(language: InvoiceLanguage): String = when {
        from != null && to != null -> "${dateSource.label(language)}: $from – $to"
        from != null -> language.text("${dateSource.label(language)}: من $from", "${dateSource.label(language)}: from $from")
        to != null -> language.text("${dateSource.label(language)}: حتى $to", "${dateSource.label(language)}: through $to")
        else -> language.text("تصفية بالتاريخ", "Filter by date")
    }

    fun toQuery(): InvoiceHistoryQuery {
        val value = term.trim()
        val zone = TimeZone.currentSystemDefault()
        val byInvoice = dateSource == HistoryDateSource.INVOICE
        return InvoiceHistoryQuery(
            seller = value,
            vat = value.asciiDigits(),
            invoiceFrom = if (byInvoice) from?.toString().orEmpty() else "",
            invoiceTo = if (byInvoice) to?.toString().orEmpty() else "",
            checkedFrom = if (!byInvoice && from != null) from.atStartOfDayIn(zone).toEpochMilliseconds() else Long.MIN_VALUE,
            checkedBefore = if (!byInvoice && to != null) (to + DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds() else Long.MAX_VALUE,
            category = category ?: -1,
            annotationTerm = annotationTerm.trim(),
        )
    }
}

private fun String.asciiDigits(): String = map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

private fun parseFilterDate(text: String): LocalDate? {
    val normalized = text.trim().asciiDigits()
    return if (datePattern.matches(normalized)) runCatching { LocalDate.parse(normalized) }.getOrNull() else null
}

internal enum class HistoryDateDialogMode { HISTORY, INVOICE_ONLY, DELETE }

@Composable
internal fun InvoiceHistoryDateDialog(filter: InvoiceHistoryFilter, onDismiss: () -> Unit,
    mode: HistoryDateDialogMode = HistoryDateDialogMode.HISTORY, onApply: (InvoiceHistoryFilter) -> Unit) {
    val language = LocalInvoiceLanguage.current
    var source by remember { mutableStateOf(filter.dateSource) }
    var from by remember { mutableStateOf(filter.from?.toString().orEmpty()) }
    var to by remember { mutableStateOf(filter.to?.toString().orEmpty()) }
    var category by remember { mutableStateOf(filter.category) }
    var categoryMenu by remember { mutableStateOf(false) }
    var annotationTerm by remember { mutableStateOf(filter.annotationTerm) }
    val start = parseFilterDate(from)
    val end = parseFilterDate(to)
    val startInvalid = from.isNotBlank() && start == null
    val endInvalid = to.isNotBlank() && end == null
    val reversed = start != null && end != null && start > end
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (mode) {
            HistoryDateDialogMode.HISTORY -> language.text("تصفية السجل", "Filter history")
            HistoryDateDialogMode.INVOICE_ONLY -> language.text("تصفية بالتاريخ", "Filter by date")
            HistoryDateDialogMode.DELETE -> language.text("حدد فترة الحذف", "Choose a date range to delete")
        }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != HistoryDateDialogMode.INVOICE_ONLY) HistoryDateSource.entries.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = source == choice, onClick = { source = choice }, role = Role.RadioButton
                        ).testTag("invoice-history-date-source-${choice.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = source == choice, onClick = null)
                        Text(choice.label(language))
                    }
                }
                HistoryDateField(from, { from = it }, language.text("من", "From"), "invoice-history-from", startInvalid)
                HistoryDateField(to, { to = it }, language.text("حتى", "Through"), "invoice-history-to", endInvalid || reversed)
                if (reversed) Text(language.text("تاريخ النهاية لازم يكون بعد تاريخ البداية أو يساويه.", "The end date must be on or after the start date."))
                if (mode == HistoryDateDialogMode.HISTORY) {
                    val allCategories = language.text("كل التصنيفات", "All categories")
                    Box {
                        TextButton(onClick = { categoryMenu = true }, modifier = Modifier.testTag("invoice-history-category")) {
                            Text(category?.let { invoiceCategoryLabel(it, language) } ?: allCategories)
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text(allCategories) }, onClick = {
                                category = null
                                categoryMenu = false
                            })
                            InvoiceAnnotationRules.categoryIds.forEach { option ->
                                DropdownMenuItem(text = { Text(invoiceCategoryLabel(option, language)) }, onClick = {
                                    category = option
                                    categoryMenu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = annotationTerm, onValueChange = { annotationTerm = it },
                        modifier = Modifier.fillMaxWidth().testTag("invoice-history-annotation"),
                        label = { Text(language.text("رقم الفاتورة أو الملاحظات", "Invoice number or notes")) },
                        singleLine = true,
                    )
                    TextButton(onClick = { onApply(InvoiceHistoryFilter(term = filter.term)) }) {
                        Text(language.text("مسح الفلاتر", "Clear filters"))
                    }
                } else if (mode == HistoryDateDialogMode.INVOICE_ONLY) {
                    TextButton(onClick = { onApply(filter.copy(from = null, to = null)) }) {
                        Text(language.text("مسح التاريخ", "Clear dates"))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(filter.copy(dateSource = source, from = start, to = end,
                    category = category, annotationTerm = annotationTerm)) },
                enabled = !startInvalid && !endInvalid && !reversed &&
                    (mode != HistoryDateDialogMode.DELETE || (start != null && end != null)),
                modifier = Modifier.testTag("invoice-history-date-apply"),
            ) {
                Text(if (mode == HistoryDateDialogMode.DELETE) language.text("عرض العدد", "Preview count")
                    else language.text("تطبيق", "Apply"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(language.text("إلغاء", "Cancel")) } },
    )
}

@Composable
private fun HistoryDateField(value: String, onChange: (String) -> Unit, label: String, tag: String, invalid: Boolean) {
    val language = LocalInvoiceLanguage.current
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) },
        placeholder = { Text("2026-10-20") },
        supportingText = if (invalid) ({ Text(language.text("اكتب التاريخ بصيغة سنة-شهر-يوم", "Enter the date as YYYY-MM-DD")) }) else null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        singleLine = true, isError = invalid,
    )
}
