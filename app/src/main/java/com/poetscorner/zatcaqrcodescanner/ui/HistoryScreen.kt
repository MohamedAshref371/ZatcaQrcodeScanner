package com.poetscorner.zatcaqrcodescanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.zatcaqrcodescanner.R
import com.poetscorner.zatcaqrcodescanner.data.HistoryEntry
import com.poetscorner.zatcaqrcodescanner.data.HistoryDateSource
import com.poetscorner.zatcaqrcodescanner.data.ScanEntity
import com.poetscorner.zatcaqrcodescanner.data.filterHistory
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HistoryScreen(entries: List<HistoryEntry>, query: String, onQuery: (String) -> Unit,
    fromDay: Long?, toDay: Long?, dateSource: HistoryDateSource,
    onOpen: (ScanEntity) -> Unit, onDelete: (ScanEntity) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val filtered by produceState(emptyList<HistoryEntry>(), entries, query, fromDay, toDay, dateSource) {
        value = withContext(Dispatchers.Default) { filterHistory(entries, query, fromDay, toDay, dateSource = dateSource) }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(value = query, onValueChange = onQuery,
                label = { Text(stringResource(R.string.history_search)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = if (query.isNotEmpty()) ({
                    TextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.history_clear_search)) }
                }) else null)
        }
        item {
            Text(stringResource(R.string.history_export_all), style = MaterialTheme.typography.bodySmall)
        }
        if (filtered.isEmpty()) item {
            Text(stringResource(if (entries.isEmpty()) R.string.no_history else R.string.history_no_matches))
        }
        items(filtered, key = { it.scan.id }) { entry ->
            val row = entry.scan
            Card(Modifier.fillMaxWidth().clickable { onOpen(row) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.seller.ifBlank { stringResource(R.string.unknown_seller) }, fontWeight = FontWeight.Bold)
                        if (entry.vat.isNotBlank()) Text(stringResource(R.string.history_vat, entry.vat),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
                        Text(DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale).format(Date(row.scannedAt)),
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (row.passed) stringResource(R.string.history_passed, row.phase) else stringResource(R.string.history_failed),
                            color = if (row.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onDelete(row) }) {
                        Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryFilterDialog(dateSource: HistoryDateSource, fromDay: Long?, toDay: Long?,
    onDismiss: () -> Unit, onApply: (HistoryDateSource, Long?, Long?) -> Unit) {
    var source by rememberSaveable { mutableStateOf(dateSource) }
    var invoiceFrom by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.INVOICE) fromDay else null) }
    var invoiceTo by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.INVOICE) toDay else null) }
    var scanFrom by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.SCAN) fromDay else null) }
    var scanTo by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.SCAN) toDay else null) }
    var pickingFrom by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val selectedFrom = if (source == HistoryDateSource.INVOICE) invoiceFrom else scanFrom
    val selectedTo = if (source == HistoryDateSource.INVOICE) invoiceTo else scanTo
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_filter_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.history_filter_hint), style = MaterialTheme.typography.bodySmall)
                HistoryDateSource.entries.forEach { option ->
                    val selected = source == option
                    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton,
                        onClick = { source = option }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null)
                        Text(stringResource(if (option == HistoryDateSource.INVOICE) R.string.history_invoice_date else R.string.history_scan_date),
                            Modifier.padding(start = 8.dp))
                    }
                    val from = if (option == HistoryDateSource.INVOICE) invoiceFrom else scanFrom
                    val to = if (option == HistoryDateSource.INVOICE) invoiceTo else scanTo
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = selected, onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
                            Column {
                                Text(stringResource(R.string.history_from))
                                Text(from?.let { dateFormat.format(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.history_any_date))
                            }
                        }
                        OutlinedButton(enabled = selected, onClick = { pickingFrom = false }, modifier = Modifier.weight(1f)) {
                            Column {
                                Text(stringResource(R.string.history_to))
                                Text(to?.let { dateFormat.format(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.history_any_date))
                            }
                        }
                    }
                }
                TextButton(onClick = { invoiceFrom = null; invoiceTo = null; scanFrom = null; scanTo = null }) {
                    Text(stringResource(R.string.history_clear_dates))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(source, selectedFrom, selectedTo) }) {
                Text(stringResource(R.string.history_confirm_filters))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    pickingFrom?.let { from ->
        HistoryDatePicker(initial = (if (from) selectedFrom else selectedTo) ?: (if (from) selectedTo else selectedFrom),
            minimum = if (from) null else selectedFrom, maximum = if (from) selectedTo else null,
            onDismiss = { pickingFrom = null }, onSelect = { day ->
                if (source == HistoryDateSource.INVOICE) {
                    if (from) invoiceFrom = day else invoiceTo = day
                } else {
                    if (from) scanFrom = day else scanTo = day
                }
                pickingFrom = null
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePicker(initial: Long?, minimum: Long?, maximum: Long?,
    onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let {
        LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }, selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            return (minimum == null || day >= minimum) && (maximum == null || day <= maximum)
        }
    })
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(enabled = state.selectedDateMillis != null, onClick = {
            state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
        }) { Text(stringResource(R.string.history_apply_date)) }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }) { DatePicker(state) }
}
