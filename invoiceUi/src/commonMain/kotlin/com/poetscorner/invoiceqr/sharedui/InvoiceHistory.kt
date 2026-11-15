package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.ParsedInvoice
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser
import kotlinx.coroutines.delay

data class InvoiceHistoryEntry(
    val id: Long,
    val raw: String,
    val checkedAt: Long,
    val alreadySaved: Boolean,
    val annotation: InvoiceAnnotationFields,
)

data class InvoiceHistorySummary(
    val id: Long,
    val seller: String,
    val vat: String,
    val total: String,
    val checkedAt: Long,
    val number: String,
    val category: Int,
)

data class InvoiceHistoryPage(val rows: List<InvoiceHistorySummary>, val hasMore: Boolean)

data class InvoiceHistoryQuery(
    val seller: String,
    val vat: String,
    val invoiceFrom: String,
    val invoiceTo: String,
    val checkedFrom: Long,
    val checkedBefore: Long,
)

interface InvoiceHistoryStore {
    fun save(raw: String, normalizedPayload: String, seller: String, vat: String, total: String, tax: String,
        invoiceDay: String, checkedAt: Long): InvoiceHistoryEntry?
    fun page(beforeId: Long, limit: Int, filter: InvoiceHistoryQuery): InvoiceHistoryPage?
    fun entry(id: Long): InvoiceHistoryEntry?
    fun updateAnnotation(id: Long, annotation: InvoiceAnnotationFields): Boolean
}

data class InvoiceHistoryDerivedFields(val day: String, val tax: String)

fun invoiceHistoryDerivedFields(raw: String): InvoiceHistoryDerivedFields {
    val invoice = ZatcaInvoiceParser.parse(raw)
    return InvoiceHistoryDerivedFields(invoiceHistoryDay(invoice), invoice.value(5))
}

internal fun invoiceHistoryDay(invoice: ParsedInvoice): String =
    runCatching { ZatcaInvoiceParser.parseTimestamp(invoice.value(3)).date.toString() }.getOrDefault("")

private const val historyPageSize = 50

@Composable
internal fun InvoiceHistoryScreen(history: InvoiceHistoryStore, filter: InvoiceHistoryFilter,
    onFilter: (InvoiceHistoryFilter) -> Unit, onOpen: (Long) -> Unit,
    exportEnabled: Boolean = false, onExport: () -> Unit = {}) {
    var showDates by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("سجل الفواتير", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = filter.term,
                onValueChange = { onFilter(filter.copy(term = it)) },
                modifier = Modifier.fillMaxWidth().testTag("invoice-history-search"),
                label = { Text("اسم البائع أو الرقم الضريبي") },
                singleLine = true,
            )
            TextButton(onClick = { showDates = true }, modifier = Modifier.testTag("invoice-history-dates")) {
                Text(filter.dateSummary())
            }
        }
        key(filter) {
            InvoiceHistoryResults(history, filter, onOpen, exportEnabled, onExport)
        }
    }
    if (showDates) {
        InvoiceHistoryDateDialog(filter, onDismiss = { showDates = false }) {
            onFilter(it)
            showDates = false
        }
    }
}

@Composable
private fun InvoiceHistoryResults(history: InvoiceHistoryStore, filter: InvoiceHistoryFilter, onOpen: (Long) -> Unit,
    exportEnabled: Boolean, onExport: () -> Unit) {
    var page by remember(history) { mutableStateOf<InvoiceHistoryPage?>(null) }
    var rows by remember(history) { mutableStateOf(emptyList<InvoiceHistorySummary>()) }
    var loadFailed by remember(history) { mutableStateOf(false) }
    val query = remember(filter) { filter.toQuery() }

    fun loadMore() {
        val next = history.page(rows.lastOrNull()?.id ?: Long.MAX_VALUE, historyPageSize, query)
        loadFailed = next == null
        if (next != null) {
            rows = rows + next.rows
            page = next
        }
    }

    LaunchedEffect(history) {
        if (filter.term.isNotBlank()) delay(200)
        loadMore()
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("invoice-history-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onExport, enabled = exportEnabled && rows.isNotEmpty() && !loadFailed,
                modifier = Modifier.testTag("invoice-history-export-csv")) { Text("تصدير CSV") }
        }
        if (rows.isEmpty() && page != null) {
            item {
                Text(if (filter.isActive) "لا توجد فواتير مطابقة" else "لا توجد فواتير محفوظة",
                    Modifier.testTag("invoice-history-empty"))
            }
        }
        items(rows, key = { it.id }) { row ->
            Card(onClick = { onOpen(row.id) }, modifier = Modifier.fillMaxWidth().testTag("invoice-history-row")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.seller.ifBlank { "اسم البائع غير موجود" }, style = MaterialTheme.typography.titleMedium)
                    if (row.vat.isNotBlank()) {
                        Text(row.vat, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                    }
                    if (row.total.isNotBlank()) {
                        Text("الإجمالي: ${row.total}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (row.number.isNotBlank()) Text("رقم الفاتورة: ${row.number}", style = MaterialTheme.typography.bodySmall)
                    if (row.category != 0) Text(arabicInvoiceCategory(row.category), style = MaterialTheme.typography.bodySmall)
                    Text("تاريخ الفحص: ${formatInvoiceCheckTime(row.checkedAt)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (loadFailed) {
            item {
                Column {
                    Text("تعذّر تحميل السجل")
                    TextButton(onClick = ::loadMore) { Text("إعادة المحاولة") }
                }
            }
        } else if (page?.hasMore == true) {
            item { TextButton(onClick = ::loadMore) { Text("عرض المزيد") } }
        }
    }
}
