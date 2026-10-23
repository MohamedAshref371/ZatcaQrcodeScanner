package com.poetscorner.invoiceqr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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

internal fun StatisticsPreset.label(): Int = when (this) {
    StatisticsPreset.THIS_MONTH -> R.string.stats_this_month
    StatisticsPreset.LAST_MONTH -> R.string.stats_last_month
    StatisticsPreset.THIS_QUARTER -> R.string.stats_this_quarter
    StatisticsPreset.LAST_QUARTER -> R.string.stats_last_quarter
    StatisticsPreset.THIS_YEAR -> R.string.stats_this_year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsScreen(state: StatisticsState, onChange: (StatisticsRequest) -> Unit, onRetry: () -> Unit,
    onOpenGroup: (StatisticsKey) -> Unit = {}, onExport: () -> Unit = {}, exportEnabled: Boolean = true,
    sellerSuggestions: suspend (String) -> List<String> = { emptyList() }) {
    val request = state.request
    val listState = rememberLazyListState()
    var scrollRequest by rememberSaveable { mutableStateOf(request) }
    LaunchedEffect(request, state.loading, state.failed) {
        if (!state.loading && !state.failed && scrollRequest != request) {
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
        StatisticsSort.TOTAL to R.string.stats_total_including_tax, StatisticsSort.TAX to R.string.tag_tax)
    LazyColumn(Modifier.fillMaxSize().testTag("statistics_list"), state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    SectionHeading(stringResource(R.string.nav_statistics), stringResource(R.string.stats_hint))
                }
                val filterState = stringResource(if (request.filtered) R.string.stats_filters_active else R.string.stats_filters_inactive)
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.history_filter_title)) } },
                    state = rememberTooltipState()) {
                    IconButton(onClick = { filters = true },
                        modifier = Modifier.size(48.dp).semantics { stateDescription = filterState }) {
                        BadgedBox(badge = { if (request.filtered) Badge() }) {
                            AppIcon(R.drawable.ic_filter, description = stringResource(R.string.history_filter_title))
                        }
                    }
                }
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.stats_export)) } },
                    state = rememberTooltipState()) {
                    IconButton(onClick = onExport,
                        enabled = exportEnabled && !state.loading && !state.failed && state.result.groups.isNotEmpty(),
                        modifier = Modifier.size(48.dp)) {
                        AppIcon(R.drawable.ic_download, description = stringResource(R.string.stats_export))
                    }
                }
            }
        }
        item {
            CompactStatisticsChoice(stringResource(R.string.stats_group_by),
                stringResource(groupSortLabel), request.grouping.name,
                StatisticsGrouping.entries.map { it.name to stringResource(it.label()) }, modifier = Modifier.fillMaxWidth()) {
                onChange(request.copy(grouping = StatisticsGrouping.valueOf(it)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CompactStatisticsChoice(stringResource(R.string.stats_sort_by),
                    stringResource(sortLabels.getValue(request.sort)), request.sort.name,
                    StatisticsSort.entries.map { it.name to stringResource(sortLabels.getValue(it)) }, modifier = Modifier.weight(1f)) {
                    onChange(request.copy(sort = StatisticsSort.valueOf(it)))
                }
                val direction = stringResource(if (request.descending) R.string.stats_descending else R.string.stats_ascending)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(direction) } }, state = rememberTooltipState()) {
                        IconButton(onClick = { onChange(request.copy(descending = !request.descending)) },
                            modifier = Modifier.size(48.dp).semantics { stateDescription = direction }) {
                            AppIcon(R.drawable.ic_sort_direction,
                                modifier = Modifier.rotate(if (request.descending) 180f else 0f),
                                description = stringResource(R.string.stats_sort_direction))
                        }
                    }
                }
            }
            if (request.filtered) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = {
                    onChange(request.copy(seller = "", vat = "", excludedVat = "", vatMode = StatisticsVatMode.INCLUDE,
                        fromDay = null, toDay = null, datePreset = null))
                }) { Text(stringResource(R.string.stats_clear_filters)) }
            }
            if (request.seller.isNotBlank()) Text(stringResource(R.string.stats_filter_value,
                stringResource(R.string.tag_seller), request.seller), style = MaterialTheme.typography.bodySmall)
            if (request.vat.isNotBlank()) Text(stringResource(R.string.stats_filter_value,
                stringResource(R.string.tag_vat), request.vat), style = MaterialTheme.typography.bodySmall)
            if (request.excludedVat.isNotBlank()) Text(stringResource(R.string.stats_filter_value,
                stringResource(R.string.stats_exclude_vat), request.excludedVat), style = MaterialTheme.typography.bodySmall)
            request.fromDay?.let { Text(stringResource(R.string.stats_filter_value, stringResource(R.string.history_from),
                dates.format(LocalDate.ofEpochDay(it))), style = MaterialTheme.typography.bodySmall) }
            request.toDay?.let { Text(stringResource(R.string.stats_filter_value, stringResource(R.string.history_to),
                dates.format(LocalDate.ofEpochDay(it))), style = MaterialTheme.typography.bodySmall) }
        }
        item(key = "statistics_progress") {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (state.failed) item {
            Text(stringResource(R.string.history_load_error))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
        }
        if (state.hasResult) {
            item(key = "statistics_summary") {
                StatisticsCard(stringResource(R.string.stats_summary_with_groups, state.result.groups.size),
                    state.result.summary, summary = true)
            }
            if (!state.loading && !state.failed && state.result.groups.isEmpty()) item { Text(stringResource(R.string.stats_empty)) }
            itemsIndexed(state.result.groups, key = { index, _ -> "statistics_card_$index" },
                contentType = { _, _ -> "statistics_card" }) { index, group ->
                val title = statisticsGroupTitle(context, state.resultRequest.grouping, group.key)
                StatisticsCard(title, group.totals, modifier = Modifier.testTag("statistics_card_$index"),
                    onClick = if (state.loading || state.failed) null else ({ onOpenGroup(group.key) }))
            }
        }
    }
    if (filters) StatisticsFilterDialog(request, sellerSuggestions, onDismiss = { filters = false }, onApply = {
        onChange(it); filters = false
    })
}

@Composable
private fun CompactStatisticsChoice(description: String, label: String, selected: String,
    values: List<Pair<String, String>>, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(description, modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                AppIcon(R.drawable.ic_expand, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        expanded = false
                        onChange(key)
                    }, leadingIcon = if (key == selected) { { AppIcon(R.drawable.ic_check) } } else null)
                }
            }
        }
    }
}

@Composable
private fun StatisticsCard(title: String, totals: StatisticsTotals, summary: Boolean = false,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val money = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
    val actionLabel = stringResource(R.string.stats_view_invoices)
    Card(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(
        onClickLabel = actionLabel, role = Role.Button, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (summary) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor =
            if (summary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.stats_count, totals.count), style = MaterialTheme.typography.bodyMedium,
                    color = if (summary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            }
            val total = if (totals.count > 0 && totals.missingTotal == totals.count) stringResource(R.string.history_total_unavailable) else money.format(totals.total)
            val tax = if (totals.count > 0 && totals.missingTax == totals.count) stringResource(R.string.history_total_unavailable) else money.format(totals.tax)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if (totals.missingTotal > 0) R.string.stats_available_total else R.string.tag_total),
                        style = MaterialTheme.typography.labelMedium)
                    Text(total, style = MaterialTheme.typography.headlineSmall,
                        color = if (summary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if (totals.missingTax > 0) R.string.stats_available_tax else R.string.tag_tax),
                        style = MaterialTheme.typography.labelMedium)
                    Text(tax, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (totals.missingTotal > 0) Text(stringResource(R.string.stats_excluded_total, totals.missingTotal),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (totals.missingTax > 0) Text(stringResource(R.string.stats_excluded_tax, totals.missingTax),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (summary && totals.count > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                Text(stringResource(R.string.stats_groups_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun StatisticsFilterDialog(request: StatisticsRequest, sellerSuggestions: suspend (String) -> List<String>,
    onDismiss: () -> Unit, onApply: (StatisticsRequest) -> Unit) {
    var seller by rememberSaveable { mutableStateOf(request.seller) }
    var vatMode by rememberSaveable { mutableStateOf(if (request.excludedVat.isNotBlank()) StatisticsVatMode.EXCLUDE else request.vatMode) }
    var vat by rememberSaveable { mutableStateOf(if (request.excludedVat.isNotBlank()) request.excludedVat else request.vat) }
    var datePreset by rememberSaveable { mutableStateOf(request.datePreset) }
    var from by rememberSaveable { mutableStateOf(request.fromDay) }
    var to by rememberSaveable { mutableStateOf(request.toDay) }
    var pickingFrom by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val normalizedVat = normalizeVatNumber(vat)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.history_filter_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SellerFilterField(seller, { seller = it }, sellerSuggestions)
                Column(verticalArrangement = Arrangement.spacedBy((-8).dp)) {
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatisticsVatMode.entries.forEach { mode ->
                            val selected = vatMode == mode
                            FilterChip(selected = selected, onClick = { vatMode = mode },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary),
                                leadingIcon = if (selected) {
                                    { AppIcon(R.drawable.ic_check, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                                label = { Text(stringResource(if (mode == StatisticsVatMode.INCLUDE) R.string.stats_vat_include else R.string.stats_vat_exclude)) })
                        }
                    }
                    OutlinedTextField(vat, { vat = it }, label = {
                        Text(stringResource(if (vatMode == StatisticsVatMode.EXCLUDE) R.string.stats_exclude_vat else R.string.tag_vat))
                    }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text(stringResource(R.string.stats_date_hint), style = MaterialTheme.typography.bodySmall)
                val today = LocalDate.now()
                Choice(stringResource(R.string.stats_date_shortcuts),
                    datePreset?.name ?: stringResource(if (from == null && to == null) R.string.stats_choose_period else R.string.stats_custom_period),
                    StatisticsPreset.entries.map { it.name to stringResource(it.label()) }) { selected ->
                    val period = request.withPeriod(StatisticsPreset.valueOf(selected), today)
                    datePreset = period.datePreset
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
                TextButton(onClick = { seller = ""; vat = ""; vatMode = StatisticsVatMode.INCLUDE; from = null; to = null; datePreset = null }) {
                    Text(stringResource(R.string.stats_clear_filters))
                }
            }
        }, confirmButton = { TextButton(onClick = {
            onApply(request.copy(seller = seller.trim(), fromDay = from, toDay = to, datePreset = datePreset)
                .withVatFilter(vatMode, normalizedVat))
        }) {
            Text(stringResource(R.string.history_apply_date))
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    pickingFrom?.let { isFrom ->
        HistoryDatePicker(initial = (if (isFrom) from else to) ?: (if (isFrom) to else from),
            minimum = if (isFrom) null else from, maximum = if (isFrom) to else null,
            onDismiss = { pickingFrom = null }, onSelect = {
                datePreset = null
                if (isFrom) from = it else to = it
                pickingFrom = null
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellerFilterField(value: String, onValueChange: (String) -> Unit,
    suggestionsFor: suspend (String) -> List<String>) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val displayedValue = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    SideEffect { fieldValue = displayedValue }
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val lookup by rememberUpdatedState(suggestionsFor)
    LaunchedEffect(value, focused, dismissed) {
        if (focused && !dismissed && value.isNotBlank()) {
            kotlinx.coroutines.delay(300)
            try {
                suggestions = lookup(value)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                suggestions = emptyList()
                // Suggestions are optional; manual filtering remains available.
            }
        } else suggestions = emptyList()
    }
    val expanded = focused && !dismissed && value.isNotBlank() && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { dismissed = !it }) {
        OutlinedTextField(displayedValue, {
            fieldValue = it
            if (it.text != value) {
                dismissed = false
                onValueChange(it.text)
            }
        }, label = { Text(stringResource(R.string.tag_seller)) }, singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                .onFocusChanged { focused = it.isFocused })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { dismissed = true }) {
            suggestions.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    dismissed = true
                    suggestions = emptyList()
                    fieldValue = TextFieldValue(name, TextRange(name.length))
                    onValueChange(name)
                })
            }
        }
    }
}
