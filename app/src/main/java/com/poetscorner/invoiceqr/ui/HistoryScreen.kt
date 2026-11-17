package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryDeletionPeriod
import com.poetscorner.invoiceqr.data.HistoryDeletionPlan
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.InvoiceCategories
import com.poetscorner.invoiceqr.data.label
import com.poetscorner.invoiceqr.data.StatisticsSelection
import com.poetscorner.invoiceqr.data.StatisticsGroupBreakdownState
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
    actions: (@Composable () -> Unit)? = null, groupTitle: String? = null,
    onClearFilters: () -> Unit = {}, groupSelection: StatisticsSelection? = null,
    groupBreakdown: StatisticsGroupBreakdownState? = null, onBreakdownRetry: () -> Unit = {}) {
    val listState = rememberLazyListState()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    var scrollFilter by rememberSaveable { mutableStateOf(state.filter) }
    val locale = LocalConfiguration.current.locales[0]
    val countFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val amountFormat = remember(locale) { NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    } }
    val showGroupBreakdown = groupSelection?.request?.categoryCalculationActive == true
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
    ScrollToTopContainer(listState) {
    LazyColumn(Modifier.fillMaxSize().testTag("history_list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (groupTitle == null) item {
            if (largeText) Column(Modifier.fillMaxWidth()) {
                SectionHeading(stringResource(R.string.history_heading), stringResource(R.string.history_subtitle))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions?.invoke() }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionHeading(stringResource(R.string.history_heading), stringResource(R.string.history_subtitle))
                }
                actions?.invoke()
            }
        } else {
            item { SectionHeading(groupTitle, stringResource(R.string.stats_view_invoices)) }
            if (actions != null) item { actions() }
        }
        if (showGroupBreakdown) item(key = "statistics_breakdown") {
            val current = groupBreakdown?.takeIf { it.selection == groupSelection }
            val breakdown = current?.result
            when {
                current == null || current.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                current.failed -> {
                    Text(stringResource(R.string.history_load_error))
                    TextButton(onClick = onBreakdownRetry) { Text(stringResource(R.string.history_retry)) }
                }
                breakdown != null -> StatisticsBreakdownCard(breakdown)
            }
        }
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
        if (groupTitle == null && (state.filter.category != null || state.filter.annotationQuery.isNotBlank() ||
                state.filter.fromDay != null || state.filter.toDay != null)) item {
            val summary = historyActiveFiltersSummary(state.filter)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.history_clear_all_filters))
                }
            }
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
        if (!showGroupBreakdown && (state.entries.isNotEmpty() || (!state.loading && !state.failed))) item(key = "history_summary") {
            val filtered = state.displayedFilter.query.isNotBlank() || state.displayedFilter.annotationQuery.isNotBlank() ||
                state.displayedFilter.fromDay != null || state.displayedFilter.toDay != null ||
                state.displayedFilter.category != null
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
                        state.amount?.let { amountFormat.format(it.toPlainString().toBigDecimal()) } ?: stringResource(R.string.history_total_unavailable)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
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
                        if (row.invoiceNumber.isNotBlank()) Text(listOfNotNull(
                            stringResource(R.string.invoice_number) + ": " + row.invoiceNumber,
                            row.category.takeIf { it != 0 }?.let { stringResource(InvoiceCategories.label(it)) }
                        ).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (row.category != 0) Text(
                            stringResource(R.string.invoice_category) + ": " +
                                stringResource(InvoiceCategories.label(row.category)),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
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
                        AppIcon(R.drawable.ic_delete, description = stringResource(R.string.history_delete_invoice_action,
                            row.seller.ifBlank { stringResource(R.string.unknown_seller) }),
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
}

@Composable
private fun historyActiveFiltersSummary(filter: HistoryFilter): String {
    val parts = mutableListOf<String>()
    filter.category?.let { parts += stringResource(InvoiceCategories.label(it)) }
    if (filter.fromDay != null || filter.toDay != null) {
        val today = LocalDate.now()
        val from = filter.fromDay
        val to = filter.toDay
        val preset = when {
            from == today.toEpochDay() && to == from -> R.string.history_today
            from == today.minusDays(1).toEpochDay() && to == from -> R.string.history_yesterday
            from == today.minusDays(6).toEpochDay() && to == today.toEpochDay() -> R.string.history_last_seven_days
            from == today.withDayOfMonth(1).toEpochDay() && to == today.toEpochDay() -> R.string.stats_this_month
            from == today.minusMonths(1).withDayOfMonth(1).toEpochDay() &&
                to == today.withDayOfMonth(1).minusDays(1).toEpochDay() -> R.string.history_last_month
            else -> null
        }
        val locale = LocalConfiguration.current.locales[0]
        val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale) }
        val range = when {
            preset != null -> stringResource(preset)
            from == to -> format.format(LocalDate.ofEpochDay(requireNotNull(from)))
            from != null && to != null ->
                "${format.format(LocalDate.ofEpochDay(from))}–${format.format(LocalDate.ofEpochDay(to))}"
            from != null -> stringResource(R.string.stats_filter_value,
                stringResource(R.string.history_from), format.format(LocalDate.ofEpochDay(from)))
            else -> stringResource(R.string.stats_filter_value,
                stringResource(R.string.history_to), format.format(LocalDate.ofEpochDay(requireNotNull(to))))
        }
        parts += if (filter.dateSource == HistoryDateSource.SCAN)
            stringResource(R.string.stats_filter_value, stringResource(R.string.history_scan_date), range)
        else range
    }
    filter.annotationQuery.trim().takeIf(String::isNotEmpty)?.let {
        parts += stringResource(R.string.stats_filter_value,
            stringResource(R.string.history_annotation_filter_label), it)
    }
    return parts.joinToString(" · ")
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

private fun deletionPeriodLabel(period: HistoryDeletionPeriod): Int = when (period) {
    HistoryDeletionPeriod.ALL -> R.string.delete_all
    HistoryDeletionPeriod.THREE_DAYS -> R.string.history_delete_three_days
    HistoryDeletionPeriod.SEVEN_DAYS -> R.string.history_delete_seven_days
    HistoryDeletionPeriod.ONE_MONTH -> R.string.history_delete_one_month
    HistoryDeletionPeriod.THREE_MONTHS -> R.string.history_delete_three_months
    HistoryDeletionPeriod.ONE_YEAR -> R.string.history_delete_one_year
    HistoryDeletionPeriod.TWO_YEARS -> R.string.history_delete_two_years
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDeleteSelectionDialog(onDismiss: () -> Unit,
    onDelete: (HistoryDateSource, HistoryDeletionPeriod) -> Unit) {
    var source by rememberSaveable { mutableStateOf(HistoryDateSource.SCAN) }
    var period by rememberSaveable { mutableStateOf(HistoryDeletionPeriod.TWO_YEARS) }
    var periodExpanded by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.5f
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.history_delete_scope), style = MaterialTheme.typography.bodySmall)
                if (period != HistoryDeletionPeriod.ALL) {
                    Text(stringResource(R.string.history_delete_date_source))
                    if (largeText) Column(Modifier.fillMaxWidth()) {
                        listOf(HistoryDateSource.SCAN, HistoryDateSource.INVOICE).forEach { option ->
                            FilterChip(selected = source == option, onClick = { source = option },
                                label = { Text(stringResource(if (option == HistoryDateSource.SCAN)
                                    R.string.history_scan_date else R.string.history_invoice_date)) },
                                modifier = Modifier.fillMaxWidth())
                        }
                    } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(HistoryDateSource.SCAN, HistoryDateSource.INVOICE).forEachIndexed { index, option ->
                            SegmentedButton(selected = source == option, onClick = { source = option },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                                Text(stringResource(if (option == HistoryDateSource.SCAN)
                                    R.string.history_scan_date else R.string.history_invoice_date))
                            }
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it }) {
                    OutlinedTextField(value = stringResource(deletionPeriodLabel(period)), onValueChange = {},
                        readOnly = true, label = { Text(stringResource(R.string.history_delete_period)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(periodExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false }) {
                        HistoryDeletionPeriod.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(deletionPeriodLabel(option))) },
                                onClick = { period = option; periodExpanded = false },
                                modifier = Modifier.semantics { selected = period == option })
                        }
                    }
                }
                if (source == HistoryDateSource.INVOICE && period != HistoryDeletionPeriod.ALL)
                    Text(stringResource(R.string.history_delete_invalid_date_hint),
                        style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(source, period) }) {
            Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun HistoryDeleteConfirmationDialog(plan: HistoryDeletionPlan,
    countMatching: suspend (HistoryDeletionPlan) -> Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var count by remember(plan) { mutableStateOf<Int?>(null) }
    var failed by remember(plan) { mutableStateOf(false) }
    LaunchedEffect(plan) {
        try { count = countMatching(plan) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val source = stringResource(if (plan.source == HistoryDateSource.SCAN)
        R.string.history_scan_date else R.string.history_invoice_date)
    val period = stringResource(deletionPeriodLabel(plan.period))
    val matching = count
    val empty = matching == 0
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (empty) R.string.history_delete_empty_title
            else R.string.history_delete_confirm_title)) },
        text = {
            if (empty) Text(if (plan.period == HistoryDeletionPeriod.ALL)
                stringResource(R.string.history_delete_empty_all)
            else stringResource(R.string.history_delete_empty_period, period, source))
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (plan.period == HistoryDeletionPeriod.ALL)
                    stringResource(R.string.history_delete_confirm_all)
                else stringResource(R.string.history_delete_confirm_period, period, source))
                when {
                    failed -> Text(stringResource(R.string.history_delete_error))
                    matching == null -> CircularProgressIndicator()
                    else -> Text(stringResource(R.string.history_delete_count, matching))
                }
                Text(stringResource(R.string.history_delete_irreversible),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = if (empty) onDismiss else onConfirm,
            enabled = empty || (matching != null && matching > 0 && !failed)) {
            Text(stringResource(if (empty) R.string.history_delete_empty_action
                else R.string.history_delete_confirm_action),
                color = if (empty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        } },
        dismissButton = { if (!empty) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterDialog(dateSource: HistoryDateSource, fromDay: Long?, toDay: Long?,
    categoryFilter: Int?, annotationQuery: String, onDismiss: () -> Unit,
    onApply: (HistoryDateSource, Long?, Long?, Int?, String) -> Unit) {
    var source by rememberSaveable { mutableStateOf(dateSource) }
    var selectedCategory by rememberSaveable { mutableStateOf(categoryFilter) }
    var annotationTerm by rememberSaveable { mutableStateOf(annotationQuery) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    var customRangeOpen by rememberSaveable { mutableStateOf(false) }
    var invoiceFrom by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.INVOICE) fromDay else null) }
    var invoiceTo by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.INVOICE) toDay else null) }
    var scanFrom by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.SCAN) fromDay else null) }
    var scanTo by rememberSaveable { mutableStateOf(if (dateSource == HistoryDateSource.SCAN) toDay else null) }
    var pickingFrom by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val selectedFrom = if (source == HistoryDateSource.INVOICE) invoiceFrom else scanFrom
    val selectedTo = if (source == HistoryDateSource.INVOICE) invoiceTo else scanTo
    val today = LocalDate.now()
    val presets = listOf(
        Triple(R.string.history_today, today, today),
        Triple(R.string.history_yesterday, today.minusDays(1), today.minusDays(1)),
        Triple(R.string.history_last_seven_days, today.minusDays(6), today),
        Triple(R.string.stats_this_month, today.withDayOfMonth(1), today),
        Triple(R.string.history_last_month, today.minusMonths(1).withDayOfMonth(1), today.withDayOfMonth(1).minusDays(1))
    )
    val matchingPreset = presets.firstOrNull { (_, from, to) ->
        selectedFrom == from.toEpochDay() && selectedTo == to.toEpochDay()
    }
    val selectedPeriodLabel = when {
        customRangeOpen -> R.string.history_custom_range
        selectedFrom == null && selectedTo == null -> R.string.history_any_period
        matchingPreset != null -> matchingPreset.first
        else -> R.string.history_custom_range
    }
    val showCustomRange = customRangeOpen ||
        (selectedFrom != null || selectedTo != null) && matchingPreset == null
    fun setRange(from: Long?, to: Long?) {
        if (source == HistoryDateSource.INVOICE) {
            invoiceFrom = from; invoiceTo = to
        } else {
            scanFrom = from; scanTo = to
        }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_filter_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.history_filter_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = annotationTerm, onValueChange = { annotationTerm = it },
                    label = { Text(stringResource(R.string.history_annotation_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(value = stringResource(selectedCategory?.let(InvoiceCategories::label)
                        ?: R.string.history_all_categories), onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.invoice_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.history_all_categories)) },
                            onClick = { selectedCategory = null; categoryExpanded = false },
                            modifier = Modifier.semantics { selected = selectedCategory == null })
                        InvoiceCategories.ids.forEach { id ->
                            DropdownMenuItem(text = { Text(stringResource(InvoiceCategories.label(id))) },
                                onClick = { selectedCategory = id; categoryExpanded = false },
                                modifier = Modifier.semantics { selected = selectedCategory == id })
                        }
                    }
                }
                if (largeText) Column(Modifier.fillMaxWidth()) {
                    HistoryDateSource.entries.forEach { option ->
                        FilterChip(selected = source == option, onClick = { source = option; customRangeOpen = false },
                            label = { Text(stringResource(if (option == HistoryDateSource.INVOICE)
                                R.string.history_invoice_date else R.string.history_scan_date)) },
                            modifier = Modifier.fillMaxWidth())
                    }
                } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HistoryDateSource.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = source == option,
                            onClick = { source = option; customRangeOpen = false },
                            shape = SegmentedButtonDefaults.itemShape(index,
                                HistoryDateSource.entries.size)) {
                            Text(stringResource(if (option == HistoryDateSource.INVOICE)
                                R.string.history_invoice_date else R.string.history_scan_date))
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it }) {
                    OutlinedTextField(value = stringResource(selectedPeriodLabel), onValueChange = {},
                        readOnly = true, label = { Text(stringResource(R.string.history_period)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(periodExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                        HistoryPeriodMenuItem(R.string.history_any_period,
                            selectedPeriodLabel == R.string.history_any_period,
                            modifier = Modifier.fillMaxWidth()) {
                            setRange(null, null); customRangeOpen = false; periodExpanded = false
                        }
                        if (largeText) presets.take(2).forEach { (label, from, to) ->
                            HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                modifier = Modifier.fillMaxWidth()) {
                                setRange(from.toEpochDay(), to.toEpochDay())
                                customRangeOpen = false; periodExpanded = false
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            presets.take(2).forEach { (label, from, to) ->
                                HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                    modifier = Modifier.weight(1f)) {
                                    setRange(from.toEpochDay(), to.toEpochDay())
                                    customRangeOpen = false; periodExpanded = false
                                }
                            }
                        }
                        val (weekLabel, weekFrom, weekTo) = presets[2]
                        HistoryPeriodMenuItem(weekLabel, selectedPeriodLabel == weekLabel,
                            modifier = Modifier.fillMaxWidth()) {
                            setRange(weekFrom.toEpochDay(), weekTo.toEpochDay())
                            customRangeOpen = false; periodExpanded = false
                        }
                        if (largeText) presets.drop(3).forEach { (label, from, to) ->
                            HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                modifier = Modifier.fillMaxWidth()) {
                                setRange(from.toEpochDay(), to.toEpochDay())
                                customRangeOpen = false; periodExpanded = false
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            presets.drop(3).forEach { (label, from, to) ->
                                HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                    modifier = Modifier.weight(1f)) {
                                    setRange(from.toEpochDay(), to.toEpochDay())
                                    customRangeOpen = false; periodExpanded = false
                                }
                            }
                        }
                        HistoryPeriodMenuItem(R.string.history_custom_range,
                            selectedPeriodLabel == R.string.history_custom_range,
                            modifier = Modifier.fillMaxWidth()) {
                            customRangeOpen = true; periodExpanded = false
                        }
                    }
                }
                if (showCustomRange) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(R.string.history_from) + ": " +
                                (selectedFrom?.let { dateFormat.format(LocalDate.ofEpochDay(it)) }
                                    ?: stringResource(R.string.history_any_date)))
                        }
                        OutlinedButton(onClick = { pickingFrom = false }, modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(R.string.history_to) + ": " +
                                (selectedTo?.let { dateFormat.format(LocalDate.ofEpochDay(it)) }
                                    ?: stringResource(R.string.history_any_date)))
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium) {
                            Column {
                                Text(stringResource(R.string.history_from))
                                Text(selectedFrom?.let { dateFormat.format(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.history_any_date))
                            }
                        }
                        OutlinedButton(onClick = { pickingFrom = false }, modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium) {
                            Column {
                                Text(stringResource(R.string.history_to))
                                Text(selectedTo?.let { dateFormat.format(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.history_any_date))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(source, selectedFrom, selectedTo, selectedCategory, annotationTerm.trim()) }) {
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
                customRangeOpen = true
                pickingFrom = null
            })
    }
}

@Composable
private fun HistoryPeriodMenuItem(label: Int, selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), maxLines = 2) },
        onClick = onClick,
        modifier = modifier.background(
            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            MaterialTheme.shapes.small).semantics { this.selected = selected },
        colors = MenuDefaults.itemColors(textColor = if (selected)
            MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 8.dp))
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
