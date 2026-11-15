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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

data class InvoiceHistoryEntry(val raw: String, val checkedAt: Long, val alreadySaved: Boolean)

data class InvoiceHistorySummary(
    val id: Long,
    val seller: String,
    val vat: String,
    val total: String,
    val checkedAt: Long,
)

data class InvoiceHistoryPage(val rows: List<InvoiceHistorySummary>, val hasMore: Boolean)

interface InvoiceHistoryStore {
    fun save(raw: String, normalizedPayload: String, seller: String, vat: String, total: String, checkedAt: Long): InvoiceHistoryEntry?
    fun page(beforeId: Long, limit: Int): InvoiceHistoryPage?
    fun entry(id: Long): InvoiceHistoryEntry?
}

private const val historyPageSize = 50

@Composable
internal fun InvoiceHistoryScreen(history: InvoiceHistoryStore, onOpen: (Long) -> Unit) {
    var page by remember(history) { mutableStateOf<InvoiceHistoryPage?>(null) }
    var rows by remember(history) { mutableStateOf(emptyList<InvoiceHistorySummary>()) }
    var loadFailed by remember(history) { mutableStateOf(false) }

    fun loadMore() {
        val next = history.page(rows.lastOrNull()?.id ?: Long.MAX_VALUE, historyPageSize)
        loadFailed = next == null
        if (next != null) {
            rows = rows + next.rows
            page = next
        }
    }

    LaunchedEffect(history) { loadMore() }

    LazyColumn(
        Modifier.fillMaxSize().testTag("invoice-history-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("سجل الفواتير", style = MaterialTheme.typography.titleLarge) }
        if (rows.isEmpty() && page != null) {
            item { Text("لا توجد فواتير محفوظة", Modifier.testTag("invoice-history-empty")) }
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
