package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceAnnotationRules
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsOptions
import com.poetscorner.invoiceqr.shared.StatisticsCategoryCalculation
import com.poetscorner.invoiceqr.shared.StatisticsGrouping

class InvoiceStatisticsQuery internal constructor(
    internal val options: InvoiceStatisticsOptions,
    val from: String,
    val to: String,
    val language: InvoiceLanguage,
)

data class InvoiceStatisticsAmounts(
    val count: Int,
    val total: String,
    val tax: String,
    val missingTotal: Int,
    val missingTax: Int,
    val ignored: Int,
)

data class InvoiceStatisticsSection(val title: String, val amounts: InvoiceStatisticsAmounts)
data class InvoiceStatisticsSnapshot(val summary: InvoiceStatisticsAmounts, val groups: List<InvoiceStatisticsSection>)

fun interface InvoiceStatisticsObserver {
    fun complete(snapshot: InvoiceStatisticsSnapshot?)
}

interface InvoiceStatisticsSource {
    fun loadStatistics(query: InvoiceStatisticsQuery, observer: InvoiceStatisticsObserver)
}

internal data class InvoiceStatisticsSettings(
    val dates: InvoiceHistoryFilter = InvoiceHistoryFilter(),
    val grouping: StatisticsGrouping = StatisticsGrouping.SELLER,
    val calculations: Map<Int, StatisticsCategoryCalculation> = emptyMap(),
)

@Composable
internal fun InvoiceStatisticsScreen(source: InvoiceStatisticsSource, settings: InvoiceStatisticsSettings,
    onChange: (InvoiceStatisticsSettings) -> Unit, exportEnabled: Boolean = false,
    onExport: (InvoiceStatisticsSnapshot, String, Boolean) -> Unit = { _, _, _ -> }) {
    val language = LocalInvoiceLanguage.current
    var retries by remember { mutableIntStateOf(0) }
    var showDates by remember { mutableStateOf(false) }
    var showCalculations by remember { mutableStateOf(false) }
    val query = remember(settings, retries, language) {
        InvoiceStatisticsQuery(InvoiceStatisticsOptions(grouping = settings.grouping, categoryCalculations = settings.calculations),
            settings.dates.from?.toString().orEmpty(), settings.dates.to?.toString().orEmpty(), language)
    }
    val snapshot = remember(query) { mutableStateOf<InvoiceStatisticsSnapshot?>(null) }
    val loading = remember(query) { mutableStateOf(true) }
    LaunchedEffect(source, query) {
        source.loadStatistics(query) {
            snapshot.value = it
            loading.value = false
        }
    }
    val result = snapshot.value
    LazyColumn(
        Modifier.fillMaxSize().testTag("invoice-statistics"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(language.text("الإحصاءات", "Statistics"), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    result?.let { onExport(it, statisticsGroupingLabel(settings.grouping, language),
                        settings.grouping != StatisticsGrouping.CATEGORY && settings.calculations.isNotEmpty()) }
                }, enabled = exportEnabled && result != null && result.summary.count > 0,
                    modifier = Modifier.testTag("invoice-statistics-export-csv")) { Text(language.text("تصدير CSV", "Export CSV")) }
                StatisticsChoice(language.text("التجميع حسب", "Group by"), settings.grouping, StatisticsGrouping.entries.filterNot { it == StatisticsGrouping.HALF_YEAR },
                    { statisticsGroupingLabel(it, language) }, "invoice-statistics-group") { onChange(settings.copy(grouping = it)) }
                TextButton(onClick = { showDates = true }) { Text(settings.dates.dateSummary(language)) }
                if (settings.grouping != StatisticsGrouping.CATEGORY) {
                    TextButton(onClick = { showCalculations = true }, modifier = Modifier.testTag("invoice-statistics-calculations")) {
                        Text(language.text("طريقة احتساب التصنيفات", "How categories affect totals"))
                    }
                }
            }
        }
        when {
            loading.value -> item { CircularProgressIndicator() }
            result == null -> item {
                Text(language.text("تعذّر تحميل الإحصاءات", "Could not load statistics"))
                TextButton(onClick = { retries++ }) { Text(language.text("إعادة المحاولة", "Retry")) }
            }
            result.summary.count == 0 -> item { Text(language.text("لا توجد فواتير للفترة المختارة", "No invoices in the selected period")) }
            else -> {
                item { StatisticsCard(language.text("الإجماليات", "Totals"), result.summary, Modifier.testTag("invoice-statistics-summary"), summary = true) }
                items(result.groups) { group -> StatisticsCard(group.title, group.amounts) }
            }
        }
    }
    if (showDates) InvoiceHistoryDateDialog(settings.dates, onDismiss = { showDates = false }, mode = HistoryDateDialogMode.INVOICE_ONLY) {
        onChange(settings.copy(dates = it))
        showDates = false
    }
    if (showCalculations) StatisticsCalculationDialog(settings.calculations, onDismiss = { showCalculations = false }) {
        onChange(settings.copy(calculations = it))
        showCalculations = false
    }
}

private fun statisticsGroupingLabel(grouping: StatisticsGrouping, language: InvoiceLanguage): String = when (grouping) {
    StatisticsGrouping.SELLER -> language.text("البائع", "Seller")
    StatisticsGrouping.VAT -> language.text("الرقم الضريبي", "VAT number")
    StatisticsGrouping.CATEGORY -> language.text("التصنيف", "Category")
    StatisticsGrouping.DAY -> language.text("اليوم", "Day")
    StatisticsGrouping.MONTH -> language.text("الشهر", "Month")
    StatisticsGrouping.QUARTER -> language.text("الربع", "Quarter")
    StatisticsGrouping.HALF_YEAR -> language.text("نصف السنة", "Half-year")
    StatisticsGrouping.YEAR -> language.text("السنة", "Year")
}

@Composable
private fun StatisticsCard(title: String, amounts: InvoiceStatisticsAmounts, modifier: Modifier = Modifier,
    summary: Boolean = false) {
    val language = LocalInvoiceLanguage.current
    val included = amounts.count - amounts.ignored
    val missing = language.text("غير متاح", "Unavailable")
    val notices = buildList {
        if (amounts.missingTotal > 0) add(language.text(
            "فواتير بدون إجمالي صالح: ${amounts.missingTotal}", "Invoices without a valid total: ${amounts.missingTotal}"))
        if (amounts.missingTax > 0) add(language.text(
            "فواتير بدون ضريبة صالحة: ${amounts.missingTax}", "Invoices without valid VAT: ${amounts.missingTax}"))
        if (amounts.ignored > 0) add(language.text(
            "مستبعدة من المبالغ: ${amounts.ignored}", "Excluded from totals: ${amounts.ignored}"))
    }
    InvoiceStatisticsCard(
        title = title, count = language.text("عدد الفواتير: ${amounts.count}", "Invoices: ${amounts.count}"),
        totalLabel = language.text("إجمالي المبالغ", "Total amount"),
        total = if (included > 0 && amounts.missingTotal == included) missing else amounts.total,
        vatLabel = language.text("إجمالي الضريبة", "Total VAT"),
        vat = if (included > 0 && amounts.missingTax == included) missing else amounts.tax,
        notices = notices, modifier = modifier, summary = summary,
    )
}

@Composable
private fun <T> StatisticsChoice(prefix: String, value: T, choices: List<T>, label: (T) -> String, tag: String,
    onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
            Text("$prefix: ${label(value)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(label(choice)) }, onClick = { onChange(choice); expanded = false })
            }
        }
    }
}

@Composable
private fun StatisticsCalculationDialog(current: Map<Int, StatisticsCategoryCalculation>, onDismiss: () -> Unit,
    onApply: (Map<Int, StatisticsCategoryCalculation>) -> Unit) {
    val language = LocalInvoiceLanguage.current
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("طريقة احتساب التصنيفات", "How categories affect totals")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InvoiceAnnotationRules.categoryIds.forEach { category ->
                    val calculation = draft[category] ?: StatisticsCategoryCalculation.ADD
                    StatisticsChoice(invoiceCategoryLabel(category, language), calculation, StatisticsCategoryCalculation.entries,
                        { when (it) {
                            StatisticsCategoryCalculation.ADD -> language.text("إضافة", "Add")
                            StatisticsCategoryCalculation.SUBTRACT -> language.text("طرح", "Subtract")
                            StatisticsCategoryCalculation.IGNORE -> language.text("تجاهل المبالغ", "Exclude from totals")
                        } }, "invoice-statistics-category-$category") {
                        draft = if (it == StatisticsCategoryCalculation.ADD) draft - category else draft + (category to it)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text(language.text("تطبيق", "Apply")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(language.text("إلغاء", "Cancel")) } },
    )
}
