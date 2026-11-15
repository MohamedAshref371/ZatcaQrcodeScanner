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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceAnnotationRules
import com.poetscorner.invoiceqr.shared.InvoiceStatisticsOptions
import com.poetscorner.invoiceqr.shared.StatisticsCategoryCalculation
import com.poetscorner.invoiceqr.shared.StatisticsGrouping

class InvoiceStatisticsQuery internal constructor(
    internal val options: InvoiceStatisticsOptions,
    val from: String,
    val to: String,
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
    onChange: (InvoiceStatisticsSettings) -> Unit) {
    var retries by remember { mutableIntStateOf(0) }
    var showDates by remember { mutableStateOf(false) }
    var showCalculations by remember { mutableStateOf(false) }
    val query = remember(settings, retries) {
        InvoiceStatisticsQuery(InvoiceStatisticsOptions(grouping = settings.grouping, categoryCalculations = settings.calculations),
            settings.dates.from?.toString().orEmpty(), settings.dates.to?.toString().orEmpty())
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
                Text("الإحصاءات", style = MaterialTheme.typography.titleLarge)
                StatisticsChoice("التجميع حسب", settings.grouping, StatisticsGrouping.entries.filterNot { it == StatisticsGrouping.HALF_YEAR },
                    ::statisticsGroupingLabel, "invoice-statistics-group") { onChange(settings.copy(grouping = it)) }
                TextButton(onClick = { showDates = true }) { Text(settings.dates.dateSummary()) }
                if (settings.grouping != StatisticsGrouping.CATEGORY) {
                    TextButton(onClick = { showCalculations = true }, modifier = Modifier.testTag("invoice-statistics-calculations")) {
                        Text("طريقة احتساب التصنيفات")
                    }
                }
            }
        }
        when {
            loading.value -> item { CircularProgressIndicator() }
            result == null -> item {
                Text("تعذّر تحميل الإحصاءات")
                TextButton(onClick = { retries++ }) { Text("إعادة المحاولة") }
            }
            result.summary.count == 0 -> item { Text("لا توجد فواتير للفترة المختارة") }
            else -> {
                item { StatisticsCard("الإجماليات", result.summary, Modifier.testTag("invoice-statistics-summary")) }
                items(result.groups) { group -> StatisticsCard(group.title, group.amounts) }
            }
        }
    }
    if (showDates) InvoiceHistoryDateDialog(settings.dates, onDismiss = { showDates = false }, invoiceOnly = true) {
        onChange(settings.copy(dates = it))
        showDates = false
    }
    if (showCalculations) StatisticsCalculationDialog(settings.calculations, onDismiss = { showCalculations = false }) {
        onChange(settings.copy(calculations = it))
        showCalculations = false
    }
}

private fun statisticsGroupingLabel(grouping: StatisticsGrouping): String = when (grouping) {
    StatisticsGrouping.SELLER -> "البائع"
    StatisticsGrouping.VAT -> "الرقم الضريبي"
    StatisticsGrouping.CATEGORY -> "التصنيف"
    StatisticsGrouping.DAY -> "اليوم"
    StatisticsGrouping.MONTH -> "الشهر"
    StatisticsGrouping.QUARTER -> "الربع"
    StatisticsGrouping.HALF_YEAR -> "نصف السنة"
    StatisticsGrouping.YEAR -> "السنة"
}

@Composable
private fun StatisticsCard(title: String, amounts: InvoiceStatisticsAmounts, modifier: Modifier = Modifier) {
    InvoiceFieldCard {
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val included = amounts.count - amounts.ignored
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("عدد الفواتير: ${amounts.count}")
            StatisticsAmount("إجمالي المبالغ", amounts.total, included, amounts.missingTotal)
            StatisticsAmount("إجمالي الضريبة", amounts.tax, included, amounts.missingTax)
            if (amounts.ignored > 0) Text("مستبعدة من المبالغ: ${amounts.ignored}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatisticsAmount(label: String, amount: String, included: Int, missing: Int) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(if (included > 0 && missing == included) "غير متاح" else amount,
        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
    if (missing > 0) Text("قيم مفقودة أو غير صالحة: $missing", style = MaterialTheme.typography.bodySmall)
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
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("طريقة احتساب التصنيفات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InvoiceAnnotationRules.categoryIds.forEach { category ->
                    val calculation = draft[category] ?: StatisticsCategoryCalculation.ADD
                    StatisticsChoice(arabicInvoiceCategory(category), calculation, StatisticsCategoryCalculation.entries,
                        { when (it) {
                            StatisticsCategoryCalculation.ADD -> "إضافة"
                            StatisticsCategoryCalculation.SUBTRACT -> "طرح"
                            StatisticsCategoryCalculation.IGNORE -> "تجاهل المبالغ"
                        } }, "invoice-statistics-category-$category") {
                        draft = if (it == StatisticsCategoryCalculation.ADD) draft - category else draft + (category to it)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("تطبيق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
