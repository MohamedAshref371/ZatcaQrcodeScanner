package com.poetscorner.invoiceqr.ui

import kotlinx.datetime.*
import org.jetbrains.compose.resources.StringResource
import com.poetscorner.invoiceqr.data.today
import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.cancel
import com.poetscorner.invoiceqr.resources.clear
import com.poetscorner.invoiceqr.resources.clear_title
import com.poetscorner.invoiceqr.resources.delete_all
import com.poetscorner.invoiceqr.resources.history_all_categories
import com.poetscorner.invoiceqr.resources.history_annotation_filter_label
import com.poetscorner.invoiceqr.resources.history_annotation_search
import com.poetscorner.invoiceqr.resources.history_any_date
import com.poetscorner.invoiceqr.resources.history_any_period
import com.poetscorner.invoiceqr.resources.history_apply_date
import com.poetscorner.invoiceqr.resources.history_clear_all_filters
import com.poetscorner.invoiceqr.resources.history_clear_search
import com.poetscorner.invoiceqr.resources.history_confirm_filters
import com.poetscorner.invoiceqr.resources.history_custom_range
import com.poetscorner.invoiceqr.resources.history_delete_confirm_action
import com.poetscorner.invoiceqr.resources.history_delete_confirm_all
import com.poetscorner.invoiceqr.resources.history_delete_confirm_period
import com.poetscorner.invoiceqr.resources.history_delete_confirm_title
import com.poetscorner.invoiceqr.resources.history_delete_count
import com.poetscorner.invoiceqr.resources.history_delete_date_source
import com.poetscorner.invoiceqr.resources.history_delete_empty_action
import com.poetscorner.invoiceqr.resources.history_delete_empty_all
import com.poetscorner.invoiceqr.resources.history_delete_empty_period
import com.poetscorner.invoiceqr.resources.history_delete_empty_title
import com.poetscorner.invoiceqr.resources.history_delete_error
import com.poetscorner.invoiceqr.resources.history_delete_invalid_date_hint
import com.poetscorner.invoiceqr.resources.history_delete_invoice_action
import com.poetscorner.invoiceqr.resources.history_delete_invoice_body
import com.poetscorner.invoiceqr.resources.history_delete_invoice_title
import com.poetscorner.invoiceqr.resources.history_delete_irreversible
import com.poetscorner.invoiceqr.resources.history_delete_one_month
import com.poetscorner.invoiceqr.resources.history_delete_one_year
import com.poetscorner.invoiceqr.resources.history_delete_period
import com.poetscorner.invoiceqr.resources.history_delete_scope
import com.poetscorner.invoiceqr.resources.history_delete_seven_days
import com.poetscorner.invoiceqr.resources.history_delete_skip_session
import com.poetscorner.invoiceqr.resources.history_delete_three_days
import com.poetscorner.invoiceqr.resources.history_delete_three_months
import com.poetscorner.invoiceqr.resources.history_delete_two_years
import com.poetscorner.invoiceqr.resources.history_export_filtered
import com.poetscorner.invoiceqr.resources.history_failed
import com.poetscorner.invoiceqr.resources.history_filter_hint
import com.poetscorner.invoiceqr.resources.history_filter_title
import com.poetscorner.invoiceqr.resources.history_from
import com.poetscorner.invoiceqr.resources.history_heading
import com.poetscorner.invoiceqr.resources.history_invoice_date
import com.poetscorner.invoiceqr.resources.history_invoice_total
import com.poetscorner.invoiceqr.resources.history_last_month
import com.poetscorner.invoiceqr.resources.history_last_seven_days
import com.poetscorner.invoiceqr.resources.history_load_error
import com.poetscorner.invoiceqr.resources.history_no_matches
import com.poetscorner.invoiceqr.resources.history_passed
import com.poetscorner.invoiceqr.resources.history_period
import com.poetscorner.invoiceqr.resources.history_retry
import com.poetscorner.invoiceqr.resources.history_scan_date
import com.poetscorner.invoiceqr.resources.history_search
import com.poetscorner.invoiceqr.resources.history_subtitle
import com.poetscorner.invoiceqr.resources.history_summary_count
import com.poetscorner.invoiceqr.resources.history_summary_result_count
import com.poetscorner.invoiceqr.resources.history_summary_result_total
import com.poetscorner.invoiceqr.resources.history_summary_total
import com.poetscorner.invoiceqr.resources.history_to
import com.poetscorner.invoiceqr.resources.history_today
import com.poetscorner.invoiceqr.resources.history_total_unavailable
import com.poetscorner.invoiceqr.resources.history_vat
import com.poetscorner.invoiceqr.resources.history_yesterday
import com.poetscorner.invoiceqr.resources.ic_check
import com.poetscorner.invoiceqr.resources.ic_delete
import com.poetscorner.invoiceqr.resources.ic_history
import com.poetscorner.invoiceqr.resources.ic_info
import com.poetscorner.invoiceqr.resources.ic_search
import com.poetscorner.invoiceqr.resources.invoice_category
import com.poetscorner.invoiceqr.resources.invoice_number
import com.poetscorner.invoiceqr.resources.missing
import com.poetscorner.invoiceqr.resources.no_history
import com.poetscorner.invoiceqr.resources.stats_filter_value
import com.poetscorner.invoiceqr.resources.stats_this_month
import com.poetscorner.invoiceqr.resources.stats_view_invoices
import com.poetscorner.invoiceqr.resources.unknown_seller
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.poetscorner.invoiceqr.ui.text.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.data.HistoryPageState
import com.poetscorner.invoiceqr.data.HistorySummary
import com.poetscorner.invoiceqr.data.HistoryDateSource
import com.poetscorner.invoiceqr.data.HistoryDeletionPeriod
import com.poetscorner.invoiceqr.data.HistoryDeletionPlan
import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.InvoiceCategories
import com.poetscorner.invoiceqr.data.resource
import com.poetscorner.invoiceqr.data.StatisticsSelection
import com.poetscorner.invoiceqr.data.StatisticsGroupBreakdownState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HistoryScreen(state: HistoryPageState, query: String, onQuery: (String) -> Unit,
    onLoadMore: () -> Unit, onRetry: () -> Unit,
    onOpen: (HistorySummary) -> Unit, onDelete: (HistorySummary) -> Unit,
    actions: (@Composable () -> Unit)? = null, groupTitle: String? = null,
    onClearFilters: () -> Unit = {}, groupSelection: StatisticsSelection? = null,
    groupBreakdown: StatisticsGroupBreakdownState? = null, onBreakdownRetry: () -> Unit = {}) {
    val listState = rememberLazyListState()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    var scrollFilter by rememberSaveable { mutableStateOf(state.filter) }
    val locale = uiLocaleTag()
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
                SectionHeading(stringResource(Res.string.history_heading), stringResource(Res.string.history_subtitle))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions?.invoke() }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionHeading(stringResource(Res.string.history_heading), stringResource(Res.string.history_subtitle))
                }
                actions?.invoke()
            }
        } else {
            item { SectionHeading(groupTitle, stringResource(Res.string.stats_view_invoices)) }
            if (actions != null) item { actions() }
        }
        if (showGroupBreakdown) item(key = "statistics_breakdown") {
            val current = groupBreakdown?.takeIf { it.selection == groupSelection }
            val breakdown = current?.result
            when {
                current == null || current.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                current.failed -> {
                    Text(stringResource(Res.string.history_load_error))
                    TextButton(onClick = onBreakdownRetry) { Text(stringResource(Res.string.history_retry)) }
                }
                breakdown != null -> StatisticsBreakdownCard(breakdown)
            }
        }
        if (groupTitle == null) item {
            OutlinedTextField(value = query, onValueChange = onQuery,
                label = { Text(stringResource(Res.string.history_search)) },
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { AppIcon(Res.drawable.ic_search) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = if (query.isNotEmpty()) ({
                    TextButton(onClick = { onQuery("") }) { Text(stringResource(Res.string.history_clear_search)) }
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
                    Text(stringResource(Res.string.history_clear_all_filters))
                }
            }
        }
        if (groupTitle == null) item {
            Text(stringResource(Res.string.history_export_filtered), style = MaterialTheme.typography.bodySmall,
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
                    Text(stringResource(if (filtered) Res.string.history_summary_result_count else Res.string.history_summary_count,
                        formatInteger(state.matching.toLong(), locale)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (filtered) Res.string.history_summary_result_total else Res.string.history_summary_total,
                        state.amount?.let { formatDecimal(it.toPlainString(), locale) } ?: stringResource(Res.string.history_total_unavailable)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!state.loading && state.entries.isEmpty() && !state.failed) item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Res.drawable.ic_history)
                    Text(stringResource(if (state.total == 0) Res.string.no_history else Res.string.history_no_matches),
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
                        Text(row.seller.ifBlank { stringResource(Res.string.unknown_seller) },
                            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                        if (row.vat.isNotBlank()) Text(stringResource(Res.string.history_vat, row.vat),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (row.invoiceNumber.isNotBlank()) Text(listOfNotNull(
                            stringResource(Res.string.invoice_number) + ": " + row.invoiceNumber,
                            row.category.takeIf { it != 0 }?.let { stringResource(InvoiceCategories.resource(it)) }
                        ).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (row.category != 0) Text(
                            stringResource(Res.string.invoice_category) + ": " +
                                stringResource(InvoiceCategories.resource(row.category)),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(Res.string.history_invoice_total,
                            row.total.ifBlank { stringResource(Res.string.missing) }),
                            style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val statusColor = if (row.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            AppIcon(if (row.passed) Res.drawable.ic_check else Res.drawable.ic_info,
                                modifier = Modifier.size(16.dp), tint = statusColor)
                            Text(if (row.passed) stringResource(Res.string.history_passed, row.phase) else stringResource(Res.string.history_failed),
                                style = MaterialTheme.typography.labelMedium, color = statusColor)
                        }
                    }
                    if (groupTitle == null) IconButton(onClick = { onDelete(row) }, enabled = !state.loading && !state.failed) {
                        AppIcon(Res.drawable.ic_delete, description = stringResource(Res.string.history_delete_invoice_action,
                            row.seller.ifBlank { stringResource(Res.string.unknown_seller) }),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (state.loadingMore) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.failed) item {
            Text(stringResource(Res.string.history_load_error))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.history_retry)) }
        }
    }
    }
}

@Composable
private fun historyActiveFiltersSummary(filter: HistoryFilter): String {
    val parts = mutableListOf<String>()
    filter.category?.let { parts += stringResource(InvoiceCategories.resource(it)) }
    if (filter.fromDay != null || filter.toDay != null) {
        val today = today()
        val from = filter.fromDay
        val to = filter.toDay
        val preset = when {
            from == today.toEpochDays() && to == from -> Res.string.history_today
            from == today.minus(1, DateTimeUnit.DAY).toEpochDays() && to == from -> Res.string.history_yesterday
            from == today.minus(6, DateTimeUnit.DAY).toEpochDays() && to == today.toEpochDays() -> Res.string.history_last_seven_days
            from == today.firstOfMonth().toEpochDays() && to == today.toEpochDays() -> Res.string.stats_this_month
            from == today.minus(1, DateTimeUnit.MONTH).firstOfMonth().toEpochDays() &&
                to == today.firstOfMonth().minus(1, DateTimeUnit.DAY).toEpochDays() -> Res.string.history_last_month
            else -> null
        }
        val locale = uiLocaleTag()
        val range = when {
            preset != null -> stringResource(preset)
            from == to -> formatDay(requireNotNull(from), locale, short = true)
            from != null && to != null ->
                "${formatDay(from, locale, short = true)}–${formatDay(to, locale, short = true)}"
            from != null -> stringResource(Res.string.stats_filter_value,
                stringResource(Res.string.history_from), formatDay(from, locale, short = true))
            else -> stringResource(Res.string.stats_filter_value,
                stringResource(Res.string.history_to), formatDay(requireNotNull(to), locale, short = true))
        }
        parts += if (filter.dateSource == HistoryDateSource.SCAN)
            stringResource(Res.string.stats_filter_value, stringResource(Res.string.history_scan_date), range)
        else range
    }
    filter.annotationQuery.trim().takeIf(String::isNotEmpty)?.let {
        parts += stringResource(Res.string.stats_filter_value,
            stringResource(Res.string.history_annotation_filter_label), it)
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun InvoiceDeleteDialog(seller: String, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var skip by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.history_delete_invoice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.history_delete_invoice_body, seller))
                Row(Modifier.fillMaxWidth().toggleable(value = skip, role = Role.Checkbox,
                    onValueChange = { skip = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = skip, onCheckedChange = null)
                    Text(stringResource(Res.string.history_delete_skip_session), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(skip) }) {
            Text(stringResource(Res.string.clear), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}

private fun deletionPeriodLabel(period: HistoryDeletionPeriod): StringResource = when (period) {
    HistoryDeletionPeriod.ALL -> Res.string.delete_all
    HistoryDeletionPeriod.THREE_DAYS -> Res.string.history_delete_three_days
    HistoryDeletionPeriod.SEVEN_DAYS -> Res.string.history_delete_seven_days
    HistoryDeletionPeriod.ONE_MONTH -> Res.string.history_delete_one_month
    HistoryDeletionPeriod.THREE_MONTHS -> Res.string.history_delete_three_months
    HistoryDeletionPeriod.ONE_YEAR -> Res.string.history_delete_one_year
    HistoryDeletionPeriod.TWO_YEARS -> Res.string.history_delete_two_years
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
        title = { Text(stringResource(Res.string.clear_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.history_delete_scope), style = MaterialTheme.typography.bodySmall)
                if (period != HistoryDeletionPeriod.ALL) {
                    Text(stringResource(Res.string.history_delete_date_source))
                    if (largeText) Column(Modifier.fillMaxWidth()) {
                        listOf(HistoryDateSource.SCAN, HistoryDateSource.INVOICE).forEach { option ->
                            FilterChip(selected = source == option, onClick = { source = option },
                                label = { Text(stringResource(if (option == HistoryDateSource.SCAN)
                                    Res.string.history_scan_date else Res.string.history_invoice_date)) },
                                modifier = Modifier.fillMaxWidth())
                        }
                    } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(HistoryDateSource.SCAN, HistoryDateSource.INVOICE).forEachIndexed { index, option ->
                            SegmentedButton(selected = source == option, onClick = { source = option },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                                Text(stringResource(if (option == HistoryDateSource.SCAN)
                                    Res.string.history_scan_date else Res.string.history_invoice_date))
                            }
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it }) {
                    OutlinedTextField(value = stringResource(deletionPeriodLabel(period)), onValueChange = {},
                        readOnly = true, label = { Text(stringResource(Res.string.history_delete_period)) },
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
                    Text(stringResource(Res.string.history_delete_invalid_date_hint),
                        style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(source, period) }) {
            Text(stringResource(Res.string.clear), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}

@Composable
fun HistoryDeleteConfirmationDialog(plan: HistoryDeletionPlan,
    countMatching: suspend (HistoryDeletionPlan) -> Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var count by remember(plan) { mutableStateOf<Int?>(null) }
    var failed by remember(plan) { mutableStateOf(false) }
    LaunchedEffect(plan) {
        try { count = countMatching(plan) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val source = stringResource(if (plan.source == HistoryDateSource.SCAN)
        Res.string.history_scan_date else Res.string.history_invoice_date)
    val period = stringResource(deletionPeriodLabel(plan.period))
    val matching = count
    val empty = matching == 0
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (empty) Res.string.history_delete_empty_title
            else Res.string.history_delete_confirm_title)) },
        text = {
            if (empty) Text(if (plan.period == HistoryDeletionPeriod.ALL)
                stringResource(Res.string.history_delete_empty_all)
            else stringResource(Res.string.history_delete_empty_period, period, source))
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (plan.period == HistoryDeletionPeriod.ALL)
                    stringResource(Res.string.history_delete_confirm_all)
                else stringResource(Res.string.history_delete_confirm_period, period, source))
                when {
                    failed -> Text(stringResource(Res.string.history_delete_error))
                    matching == null -> CircularProgressIndicator()
                    else -> Text(stringResource(Res.string.history_delete_count, matching))
                }
                Text(stringResource(Res.string.history_delete_irreversible),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = if (empty) onDismiss else onConfirm,
            enabled = empty || (matching != null && matching > 0 && !failed)) {
            Text(stringResource(if (empty) Res.string.history_delete_empty_action
                else Res.string.history_delete_confirm_action),
                color = if (empty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        } },
        dismissButton = { if (!empty) TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
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
    val locale = uiLocaleTag()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val selectedFrom = if (source == HistoryDateSource.INVOICE) invoiceFrom else scanFrom
    val selectedTo = if (source == HistoryDateSource.INVOICE) invoiceTo else scanTo
    val today = today()
    val presets = listOf(
        Triple(Res.string.history_today, today, today),
        Triple(Res.string.history_yesterday, today.minus(1, DateTimeUnit.DAY), today.minus(1, DateTimeUnit.DAY)),
        Triple(Res.string.history_last_seven_days, today.minus(6, DateTimeUnit.DAY), today),
        Triple(Res.string.stats_this_month, today.firstOfMonth(), today),
        Triple(Res.string.history_last_month, today.minus(1, DateTimeUnit.MONTH).firstOfMonth(), today.firstOfMonth().minus(1, DateTimeUnit.DAY))
    )
    val matchingPreset = presets.firstOrNull { (_, from, to) ->
        selectedFrom == from.toEpochDays() && selectedTo == to.toEpochDays()
    }
    val selectedPeriodLabel = when {
        customRangeOpen -> Res.string.history_custom_range
        selectedFrom == null && selectedTo == null -> Res.string.history_any_period
        matchingPreset != null -> matchingPreset.first
        else -> Res.string.history_custom_range
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
        title = { Text(stringResource(Res.string.history_filter_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.history_filter_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = annotationTerm, onValueChange = { annotationTerm = it },
                    label = { Text(stringResource(Res.string.history_annotation_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(value = stringResource(selectedCategory?.let(InvoiceCategories::resource)
                        ?: Res.string.history_all_categories), onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(Res.string.invoice_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.history_all_categories)) },
                            onClick = { selectedCategory = null; categoryExpanded = false },
                            modifier = Modifier.semantics { selected = selectedCategory == null })
                        InvoiceCategories.ids.forEach { id ->
                            DropdownMenuItem(text = { Text(stringResource(InvoiceCategories.resource(id))) },
                                onClick = { selectedCategory = id; categoryExpanded = false },
                                modifier = Modifier.semantics { selected = selectedCategory == id })
                        }
                    }
                }
                if (largeText) Column(Modifier.fillMaxWidth()) {
                    HistoryDateSource.entries.forEach { option ->
                        FilterChip(selected = source == option, onClick = { source = option; customRangeOpen = false },
                            label = { Text(stringResource(if (option == HistoryDateSource.INVOICE)
                                Res.string.history_invoice_date else Res.string.history_scan_date)) },
                            modifier = Modifier.fillMaxWidth())
                    }
                } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HistoryDateSource.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = source == option,
                            onClick = { source = option; customRangeOpen = false },
                            shape = SegmentedButtonDefaults.itemShape(index,
                                HistoryDateSource.entries.size)) {
                            Text(stringResource(if (option == HistoryDateSource.INVOICE)
                                Res.string.history_invoice_date else Res.string.history_scan_date))
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it }) {
                    OutlinedTextField(value = stringResource(selectedPeriodLabel), onValueChange = {},
                        readOnly = true, label = { Text(stringResource(Res.string.history_period)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(periodExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                        HistoryPeriodMenuItem(Res.string.history_any_period,
                            selectedPeriodLabel == Res.string.history_any_period,
                            modifier = Modifier.fillMaxWidth()) {
                            setRange(null, null); customRangeOpen = false; periodExpanded = false
                        }
                        if (largeText) presets.take(2).forEach { (label, from, to) ->
                            HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                modifier = Modifier.fillMaxWidth()) {
                                setRange(from.toEpochDays(), to.toEpochDays())
                                customRangeOpen = false; periodExpanded = false
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            presets.take(2).forEach { (label, from, to) ->
                                HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                    modifier = Modifier.weight(1f)) {
                                    setRange(from.toEpochDays(), to.toEpochDays())
                                    customRangeOpen = false; periodExpanded = false
                                }
                            }
                        }
                        val (weekLabel, weekFrom, weekTo) = presets[2]
                        HistoryPeriodMenuItem(weekLabel, selectedPeriodLabel == weekLabel,
                            modifier = Modifier.fillMaxWidth()) {
                            setRange(weekFrom.toEpochDays(), weekTo.toEpochDays())
                            customRangeOpen = false; periodExpanded = false
                        }
                        if (largeText) presets.drop(3).forEach { (label, from, to) ->
                            HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                modifier = Modifier.fillMaxWidth()) {
                                setRange(from.toEpochDays(), to.toEpochDays())
                                customRangeOpen = false; periodExpanded = false
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            presets.drop(3).forEach { (label, from, to) ->
                                HistoryPeriodMenuItem(label, selectedPeriodLabel == label,
                                    modifier = Modifier.weight(1f)) {
                                    setRange(from.toEpochDays(), to.toEpochDays())
                                    customRangeOpen = false; periodExpanded = false
                                }
                            }
                        }
                        HistoryPeriodMenuItem(Res.string.history_custom_range,
                            selectedPeriodLabel == Res.string.history_custom_range,
                            modifier = Modifier.fillMaxWidth()) {
                            customRangeOpen = true; periodExpanded = false
                        }
                    }
                }
                if (showCustomRange) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(Res.string.history_from) + ": " +
                                (selectedFrom?.let { formatDay(it, locale) }
                                    ?: stringResource(Res.string.history_any_date)))
                        }
                        OutlinedButton(onClick = { pickingFrom = false }, modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(Res.string.history_to) + ": " +
                                (selectedTo?.let { formatDay(it, locale) }
                                    ?: stringResource(Res.string.history_any_date)))
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium) {
                            Column {
                                Text(stringResource(Res.string.history_from))
                                Text(selectedFrom?.let { formatDay(it, locale) } ?: stringResource(Res.string.history_any_date))
                            }
                        }
                        OutlinedButton(onClick = { pickingFrom = false }, modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium) {
                            Column {
                                Text(stringResource(Res.string.history_to))
                                Text(selectedTo?.let { formatDay(it, locale) } ?: stringResource(Res.string.history_any_date))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(source, selectedFrom, selectedTo, selectedCategory, annotationTerm.trim()) }) {
                Text(stringResource(Res.string.history_confirm_filters))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
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
private fun HistoryPeriodMenuItem(label: StringResource, selected: Boolean, modifier: Modifier = Modifier,
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
        it * MILLIS_PER_DAY
    }, selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val day = utcTimeMillis.floorDiv(MILLIS_PER_DAY)
            return (minimum == null || day >= minimum) && (maximum == null || day <= maximum)
        }
    })
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(enabled = state.selectedDateMillis != null, onClick = {
            state.selectedDateMillis?.let { onSelect(it.floorDiv(MILLIS_PER_DAY)) }
        }) { Text(stringResource(Res.string.history_apply_date)) }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
    }) { DatePicker(state) }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.firstOfMonth() = LocalDate(year, month, 1)
