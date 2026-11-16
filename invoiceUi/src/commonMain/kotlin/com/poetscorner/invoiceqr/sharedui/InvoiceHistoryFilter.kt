package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
) {
    private val hasDates get() = from != null || to != null
    val isActive get() = term.isNotBlank() || hasDates

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
        )
    }
}

private fun String.asciiDigits(): String = map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

private fun parseFilterDate(text: String): LocalDate? {
    val normalized = text.trim().asciiDigits()
    return if (datePattern.matches(normalized)) runCatching { LocalDate.parse(normalized) }.getOrNull() else null
}

@Composable
internal fun InvoiceHistoryDateDialog(filter: InvoiceHistoryFilter, onDismiss: () -> Unit,
    invoiceOnly: Boolean = false, onApply: (InvoiceHistoryFilter) -> Unit) {
    val language = LocalInvoiceLanguage.current
    var source by remember { mutableStateOf(filter.dateSource) }
    var from by remember { mutableStateOf(filter.from?.toString().orEmpty()) }
    var to by remember { mutableStateOf(filter.to?.toString().orEmpty()) }
    val start = parseFilterDate(from)
    val end = parseFilterDate(to)
    val startInvalid = from.isNotBlank() && start == null
    val endInvalid = to.isNotBlank() && end == null
    val reversed = start != null && end != null && start > end
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("تصفية بالتاريخ", "Filter by date")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!invoiceOnly) HistoryDateSource.entries.forEach { choice ->
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
                TextButton(onClick = { onApply(filter.copy(from = null, to = null)) }) { Text(language.text("مسح التاريخ", "Clear dates")) }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(filter.copy(dateSource = source, from = start, to = end)) },
                enabled = !startInvalid && !endInvalid && !reversed,
                modifier = Modifier.testTag("invoice-history-date-apply")) { Text(language.text("تطبيق", "Apply")) }
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
