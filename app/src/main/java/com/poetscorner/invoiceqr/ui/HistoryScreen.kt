package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import com.poetscorner.invoiceqr.data.HistoryDateSource
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat

@Composable
internal fun HistoryScreen(state: HistoryPageState, query: String, onQuery: (String) -> Unit,
    onLoadMore: () -> Unit, onRetry: () -> Unit,
    onOpen: (HistorySummary) -> Unit, onDelete: (HistorySummary) -> Unit,
    actions: (@Composable () -> Unit)? = null, groupTitle: String? = null) {
    val listState = rememberLazyListState()
    var scrollFilter by rememberSaveable { mutableStateOf(state.filter) }
    val locale = LocalConfiguration.current.locales[0]
    val countFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val amountFormat = remember(locale) { NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    } }
    LaunchedEffect(state.filter, state.loading, state.failed) {
        if (!state.loading && !state.failed && scrollFilter != state.filter) {
            listState.scrollToItem(0)
            scrollFilter = state.filter
        }
    }
    LaunchedEffect(state.entries.size, state.loading, state.loadingMore, state.failed) {
        if (!state.loading && !state.loadingMore && !state.failed && state.hasMore) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged().collect { last ->
                    if (last >= state.entries.size - 8) onLoadMore()
                }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("history_list"), state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeading(groupTitle ?: stringResource(R.string.history_heading),
            stringResource(if (groupTitle == null) R.string.history_subtitle else R.string.stats_view_invoices)) }
        if (actions != null) item { actions() }
        if (groupTitle == null) item {
            OutlinedTextField(value = query, onValueChange = onQuery,
                label = { Text(stringResource(R.string.history_search)) },
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { AppIcon(R.drawable.ic_search) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = if (query.isNotEmpty()) ({
                    TextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.history_clear_search)) }
                }) else null)
        }
        if (groupTitle == null) item {
            Text(stringResource(R.string.history_export_filtered), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "history_progress") {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (!state.loading && state.entries.isEmpty() && !state.failed) item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(R.drawable.ic_history)
                    Text(stringResource(if (state.total == 0) R.string.no_history else R.string.history_no_matches),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.entries.isNotEmpty() || (!state.loading && !state.failed)) item(key = "history_summary") {
            val filtered = state.displayedFilter.query.isNotBlank() || state.displayedFilter.fromDay != null || state.displayedFilter.toDay != null
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("history_summary").padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(if (filtered) R.string.history_summary_result_count else R.string.history_summary_count,
                        countFormat.format(state.matching)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (filtered) R.string.history_summary_result_total else R.string.history_summary_total,
                        state.amount?.let { amountFormat.format(it) } ?: stringResource(R.string.history_total_unavailable)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Slots belong to positions so new results can replace their contents in place.
        itemsIndexed(state.entries, key = { index, _ -> "history_card_$index" }, contentType = { _, _ -> "history_card" }) { index, row ->
            Card(onClick = { onOpen(row) }, enabled = !state.loading && !state.failed,
                modifier = Modifier.fillMaxWidth().testTag("history_card_$index"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.seller.ifBlank { stringResource(R.string.unknown_seller) },
                            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                        if (row.vat.isNotBlank()) Text(stringResource(R.string.history_vat, row.vat),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.history_invoice_total,
                            row.total.ifBlank { stringResource(R.string.missing) }),
                            style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val statusColor = if (row.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            AppIcon(if (row.passed) R.drawable.ic_check else R.drawable.ic_info,
                                modifier = Modifier.size(16.dp), tint = statusColor)
                            Text(if (row.passed) stringResource(R.string.history_passed, row.phase) else stringResource(R.string.history_failed),
                                style = MaterialTheme.typography.labelMedium, color = statusColor)
                        }
                    }
                    if (groupTitle == null) IconButton(onClick = { onDelete(row) }, enabled = !state.loading && !state.failed) {
                        AppIcon(R.drawable.ic_delete, description = stringResource(R.string.clear),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (state.loadingMore) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.failed) item {
            Text(stringResource(R.string.history_load_error))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
        }
    }
}

@Composable
internal fun InvoiceDeleteDialog(seller: String, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var skip by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_invoice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.history_delete_invoice_body, seller))
                Row(Modifier.fillMaxWidth().toggleable(value = skip, role = Role.Checkbox,
                    onValueChange = { skip = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = skip, onCheckedChange = null)
                    Text(stringResource(R.string.history_delete_skip_session), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(skip) }) {
            Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
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
internal fun HistoryDatePicker(initial: Long?, minimum: Long?, maximum: Long?,
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
