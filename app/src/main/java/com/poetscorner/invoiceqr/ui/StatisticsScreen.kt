package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.data.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun StatisticsGrouping.label(): Int = when (this) {
    StatisticsGrouping.SELLER -> R.string.tag_seller
    StatisticsGrouping.VAT -> R.string.tag_vat
    StatisticsGrouping.DAY -> R.string.stats_day
    StatisticsGrouping.MONTH -> R.string.stats_month
    StatisticsGrouping.QUARTER -> R.string.stats_quarter
    StatisticsGrouping.HALF_YEAR -> R.string.stats_half_year
    StatisticsGrouping.YEAR -> R.string.stats_year
}

@Composable
internal fun StatisticsScreen(state: StatisticsState, onChange: (StatisticsRequest) -> Unit, onRetry: () -> Unit,
    onOpenGroup: (StatisticsKey) -> Unit = {}, onExport: () -> Unit = {}, exportEnabled: Boolean = true) {
    val request = state.request
    val listState = rememberLazyListState()
    var scrollRequest by rememberSaveable { mutableStateOf(request) }
    LaunchedEffect(request) {
        if (scrollRequest != request) {
            listState.scrollToItem(0)
            scrollRequest = request
        }
    }
    val context = LocalContext.current
    var filters by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val groupSortLabel = request.grouping.label()
    val sortLabels = mapOf(StatisticsSort.GROUP to groupSortLabel, StatisticsSort.COUNT to R.string.stats_count_label,
        StatisticsSort.TOTAL to R.string.tag_total, StatisticsSort.TAX to R.string.tag_tax)
    LazyColumn(Modifier.fillMaxSize().testTag("statistics_list"), state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading(stringResource(R.string.nav_statistics), stringResource(R.string.stats_hint)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filters = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_filter_title))
                }
                if (request.filtered) TextButton(onClick = {
                    onChange(request.copy(seller = "", vat = "", fromDay = null, toDay = null))
                }) { Text(stringResource(R.string.stats_clear_filters)) }
            }
            if (request.seller.isNotBlank()) Text(stringResource(R.string.stats_filter_value,
                stringResource(R.string.tag_seller), request.seller), style = MaterialTheme.typography.bodySmall)
            if (request.vat.isNotBlank()) Text(stringResource(R.string.stats_filter_value,
                stringResource(R.string.tag_vat), request.vat), style = MaterialTheme.typography.bodySmall)
            request.fromDay?.let { Text(stringResource(R.string.stats_filter_value, stringResource(R.string.history_from),
                dates.format(LocalDate.ofEpochDay(it))), style = MaterialTheme.typography.bodySmall) }
            request.toDay?.let { Text(stringResource(R.string.stats_filter_value, stringResource(R.string.history_to),
                dates.format(LocalDate.ofEpochDay(it))), style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Choice(stringResource(R.string.stats_group_by), request.grouping.name,
                StatisticsGrouping.entries.map { it.name to stringResource(it.label()) }) {
                onChange(request.copy(grouping = StatisticsGrouping.valueOf(it)))
            }
        }
        item {
            Choice(stringResource(R.string.stats_sort_by), request.sort.name,
                StatisticsSort.entries.map { it.name to stringResource(sortLabels.getValue(it)) }) {
                onChange(request.copy(sort = StatisticsSort.valueOf(it)))
            }
        }
        item {
            Choice(stringResource(R.string.stats_sort_direction), request.descending.toString(),
                listOf("false" to stringResource(R.string.stats_ascending), "true" to stringResource(R.string.stats_descending))) {
                onChange(request.copy(descending = it.toBoolean()))
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (state.failed) item {
            Text(stringResource(R.string.history_load_error))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
        } else {
            item {
                OutlinedButton(onClick = onExport, enabled = exportEnabled && state.result.groups.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stats_export)) }
            }
            item { StatisticsCard(stringResource(R.string.stats_summary), state.result.summary, summary = true) }
            if (state.result.groups.isEmpty()) item { Text(stringResource(R.string.stats_empty)) }
            items(state.result.groups, key = { it.key.text?.let { value -> "text:$value" }
                ?: it.key.periodStart?.let { day -> "day:$day" } ?: "unknown" }) { group ->
                val title = statisticsGroupTitle(context, request.grouping, group.key)
                StatisticsCard(title, group.totals, onClick = { onOpenGroup(group.key) })
            }
        }
    }
    if (filters) StatisticsFilterDialog(request, onDismiss = { filters = false }, onApply = {
        onChange(it); filters = false
    })
}

@Composable
private fun StatisticsCard(title: String, totals: StatisticsTotals, summary: Boolean = false, onClick: (() -> Unit)? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val money = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), colors = CardDefaults.cardColors(containerColor =
        if (summary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.stats_count, totals.count))
            val total = if (totals.count > 0 && totals.missingTotal == totals.count) stringResource(R.string.history_total_unavailable) else money.format(totals.total)
            val tax = if (totals.count > 0 && totals.missingTax == totals.count) stringResource(R.string.history_total_unavailable) else money.format(totals.tax)
            Text(stringResource(R.string.stats_filter_value, stringResource(if (totals.missingTotal > 0) R.string.stats_available_total else R.string.tag_total), total))
            Text(stringResource(R.string.stats_filter_value, stringResource(if (totals.missingTax > 0) R.string.stats_available_tax else R.string.tag_tax), tax))
            if (onClick != null) Text(stringResource(R.string.stats_view_invoices), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            if (totals.missingTotal > 0) Text(stringResource(R.string.stats_excluded_total, totals.missingTotal),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (totals.missingTax > 0) Text(stringResource(R.string.stats_excluded_tax, totals.missingTax),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatisticsFilterDialog(request: StatisticsRequest, onDismiss: () -> Unit, onApply: (StatisticsRequest) -> Unit) {
    var seller by rememberSaveable { mutableStateOf(request.seller) }
    var vat by rememberSaveable { mutableStateOf(request.vat) }
    var from by rememberSaveable { mutableStateOf(request.fromDay) }
    var to by rememberSaveable { mutableStateOf(request.toDay) }
    var pickingFrom by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.history_filter_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(seller, { seller = it }, label = { Text(stringResource(R.string.tag_seller)) }, singleLine = true)
                OutlinedTextField(vat, { vat = it }, label = { Text(stringResource(R.string.tag_vat)) }, singleLine = true)
                Text(stringResource(R.string.stats_date_hint), style = MaterialTheme.typography.bodySmall)
                val today = LocalDate.now()
                val selectedPreset = StatisticsPreset.entries.firstOrNull { preset ->
                    val period = request.withPeriod(preset, today)
                    period.fromDay == from && period.toDay == to
                }
                val labels = listOf(R.string.stats_this_month, R.string.stats_last_month, R.string.stats_this_quarter,
                    R.string.stats_last_quarter, R.string.stats_this_year)
                Choice(stringResource(R.string.stats_date_shortcuts),
                    selectedPreset?.name ?: stringResource(if (from == null && to == null) R.string.stats_choose_period else R.string.stats_custom_period),
                    StatisticsPreset.entries.mapIndexed { index, preset -> preset.name to stringResource(labels[index]) }) { selected ->
                    val period = request.withPeriod(StatisticsPreset.valueOf(selected), today)
                    from = period.fromDay; to = period.toDay
                }
                OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stats_filter_value, stringResource(R.string.history_from),
                        from?.let { dates.format(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.stats_any_date)))
                }
                OutlinedButton(onClick = { pickingFrom = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stats_filter_value, stringResource(R.string.history_to),
                        to?.let { dates.format(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.stats_any_date)))
                }
                TextButton(onClick = { seller = ""; vat = ""; from = null; to = null }) {
                    Text(stringResource(R.string.stats_clear_filters))
                }
            }
        }, confirmButton = { TextButton(onClick = { onApply(request.copy(seller = seller.trim(), vat = vat.trim(), fromDay = from, toDay = to)) }) {
            Text(stringResource(R.string.history_apply_date))
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    pickingFrom?.let { isFrom ->
        HistoryDatePicker(initial = (if (isFrom) from else to) ?: (if (isFrom) to else from),
            minimum = if (isFrom) null else from, maximum = if (isFrom) to else null,
            onDismiss = { pickingFrom = null }, onSelect = {
                if (isFrom) from = it else to = it
                pickingFrom = null
            })
    }
}
