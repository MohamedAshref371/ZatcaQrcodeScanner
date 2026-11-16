package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val identity: String,
)

data class InvoiceHistoryPage(val rows: List<InvoiceHistorySummary>, val hasMore: Boolean)

data class InvoiceHistoryQuery(
    val seller: String,
    val vat: String,
    val invoiceFrom: String,
    val invoiceTo: String,
    val checkedFrom: Long,
    val checkedBefore: Long,
    val category: Int,
    val annotationTerm: String,
    val statistics: InvoiceStatisticsHistoryFilter?,
) {
    constructor(seller: String, vat: String, invoiceFrom: String, invoiceTo: String, checkedFrom: Long,
        checkedBefore: Long, category: Int, annotationTerm: String) :
        this(seller, vat, invoiceFrom, invoiceTo, checkedFrom, checkedBefore, category, annotationTerm, null)
}

data class InvoiceHistoryDeletionPreview(val count: Long, val token: String)

enum class InvoiceHistoryDeletionResult { DELETED, CHANGED, FAILED }

interface InvoiceHistoryStore {
    fun save(raw: String, normalizedPayload: String, seller: String, vat: String, total: String, tax: String,
        invoiceDay: String, checkedAt: Long): InvoiceHistoryEntry?
    fun page(beforeId: Long, limit: Int, filter: InvoiceHistoryQuery): InvoiceHistoryPage?
    fun entry(id: Long): InvoiceHistoryEntry?
    fun deleteInvoice(id: Long, identity: String, checkedAt: Long): Boolean
    fun previewDeletion(filter: InvoiceHistoryQuery): InvoiceHistoryDeletionPreview?
    fun deletePeriod(filter: InvoiceHistoryQuery, preview: InvoiceHistoryDeletionPreview): InvoiceHistoryDeletionResult
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
    actionsEnabled: Boolean, exportAvailable: Boolean, onDeleted: () -> Unit, onMessage: (String) -> Unit,
    onExport: () -> Unit) {
    val language = LocalInvoiceLanguage.current
    var showFilters by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf<InvoiceHistorySummary?>(null) }
    var showDeletePeriod by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf<InvoiceHistoryDeletionSelection?>(null) }
    var revision by remember { mutableStateOf(0) }

    fun refreshAfterDeletion() {
        revision++
        onDeleted()
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(language.text("سجل الفواتير", "Invoice history"), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = filter.term,
                onValueChange = { onFilter(filter.copy(term = it)) },
                modifier = Modifier.fillMaxWidth().testTag("invoice-history-search"),
                label = { Text(language.text("اسم البائع أو الرقم الضريبي", "Seller name or VAT number")) },
                singleLine = true,
            )
            TextButton(onClick = { showFilters = true }, modifier = Modifier.testTag("invoice-history-dates")) {
                Text(filter.summary(language))
            }
        }
        key(filter, revision) {
            InvoiceHistoryResults(history, filter, onOpen, actionsEnabled, exportAvailable, onExport,
                onDeleteInvoice = { selectedInvoice = it }, onDeletePeriod = { showDeletePeriod = true })
        }
    }
    if (showFilters) {
        InvoiceHistoryDateDialog(filter, onDismiss = { showFilters = false }, mode = HistoryDateDialogMode.HISTORY) {
            onFilter(it)
            showFilters = false
        }
    }
    selectedInvoice?.let { row ->
        InvoiceDeleteConfirmation(
            title = language.text("حذف الفاتورة", "Delete invoice"),
            message = listOf(
                language.text("هل تريد حذف هذه الفاتورة من السجل؟", "Delete this invoice from history?"),
                row.seller,
                row.number,
            ).filter { it.isNotBlank() }.joinToString("\n"),
            onDismiss = { selectedInvoice = null },
            onConfirm = if (!actionsEnabled) null else ({
                selectedInvoice = null
                if (history.deleteInvoice(row.id, row.identity, row.checkedAt)) {
                    refreshAfterDeletion()
                    onMessage(language.text("تم حذف الفاتورة", "Invoice deleted"))
                } else {
                    revision++
                    onMessage(language.text("تعذّر حذف الفاتورة", "Could not delete the invoice"))
                }
            }),
        )
    }
    if (showDeletePeriod) {
        InvoiceHistoryDateDialog(InvoiceHistoryFilter(), onDismiss = { showDeletePeriod = false },
            mode = HistoryDateDialogMode.DELETE) { range ->
            showDeletePeriod = false
            val query = range.toQuery()
            val preview = history.previewDeletion(query)
            if (preview != null) {
                deletion = InvoiceHistoryDeletionSelection(query, range.dateSummary(language), preview)
            } else {
                onMessage(language.text("تعذّر حساب الفواتير في الفترة", "Could not count invoices in the period"))
            }
        }
    }
    deletion?.let { selection ->
        val count = selection.preview.count
        InvoiceDeleteConfirmation(
            title = language.text("الحذف حسب الفترة", "Delete by date range"),
            message = listOf(
                selection.label,
                language.text("عدد الفواتير المطابقة: $count", "Matching invoices: $count"),
                language.text("يشمل الحذف كل الفواتير في الفترة المحددة.", "This applies to all invoices in the selected range."),
            ).joinToString("\n"),
            onDismiss = { deletion = null },
            onConfirm = if (count == 0L || !actionsEnabled) null else ({
                when (history.deletePeriod(selection.query, selection.preview)) {
                    InvoiceHistoryDeletionResult.DELETED -> {
                        deletion = null
                        refreshAfterDeletion()
                        onMessage(language.text("تم الحذف. عدد الفواتير: $count", "Invoices deleted: $count"))
                    }
                    InvoiceHistoryDeletionResult.CHANGED -> {
                        revision++
                        deletion = history.previewDeletion(selection.query)?.let { selection.copy(preview = it) }
                        onMessage(language.text("تغيّر السجل. راجع العدد قبل الحذف.", "History changed. Review the count before deleting."))
                    }
                    InvoiceHistoryDeletionResult.FAILED -> {
                        deletion = null
                        onMessage(language.text("تعذّر حذف الفواتير", "Could not delete the invoices"))
                    }
                }
            }),
        )
    }
}

@Composable
internal fun InvoiceStatisticsHistoryScreen(history: InvoiceHistoryStore, group: InvoiceStatisticsDrillDown,
    onBack: () -> Unit, onOpen: (Long) -> Unit, exportEnabled: Boolean, onExport: () -> Unit) {
    val language = LocalInvoiceLanguage.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("invoice-statistics-back")) {
                Text(language.text("العودة إلى الإحصاءات", "Back to statistics"))
            }
            Text(group.title, style = MaterialTheme.typography.titleLarge)
        }
        key(group.filter) {
            InvoiceHistoryResults(history, group.filter, onOpen, actionsEnabled = exportEnabled,
                exportAvailable = exportEnabled, onExport = onExport, onDeleteInvoice = {},
                onDeletePeriod = null, readOnly = true)
        }
    }
}

@Composable
private fun InvoiceHistoryResults(history: InvoiceHistoryStore, filter: InvoiceHistoryFilter, onOpen: (Long) -> Unit,
    actionsEnabled: Boolean, exportAvailable: Boolean, onExport: () -> Unit, onDeleteInvoice: (InvoiceHistorySummary) -> Unit,
    onDeletePeriod: (() -> Unit)?, readOnly: Boolean = false) {
    val language = LocalInvoiceLanguage.current
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
        if (filter.term.isNotBlank() || filter.annotationTerm.isNotBlank()) delay(200)
        loadMore()
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("invoice-history-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExport, enabled = actionsEnabled && exportAvailable && rows.isNotEmpty() && !loadFailed,
                    modifier = Modifier.testTag("invoice-history-export-csv")) { Text(language.text("تصدير CSV", "Export CSV")) }
                if (onDeletePeriod != null) TextButton(onClick = onDeletePeriod, enabled = actionsEnabled,
                    modifier = Modifier.testTag("invoice-history-delete-period")) {
                    Text(language.text("حذف حسب الفترة", "Delete by date range"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (rows.isEmpty() && page != null) {
            item {
                Text(if (filter.isActive) language.text("لا توجد فواتير مطابقة", "No matching invoices") else language.text("لا توجد فواتير محفوظة", "No saved invoices"),
                    Modifier.testTag("invoice-history-empty"))
            }
        }
        items(rows, key = { it.id }) { row ->
            val seller = row.seller.ifBlank { language.text("اسم البائع غير موجود", "Seller name missing") }
            val vat = row.vat.takeIf { it.isNotBlank() }?.let { language.text("الرقم الضريبي: $it", "VAT number: $it") }
            val number = row.number.takeIf { it.isNotBlank() }?.let { language.text("رقم الفاتورة: $it", "Invoice number: $it") }
            val category = row.category.takeIf { it != 0 }?.let { invoiceCategoryLabel(it, language) }
            val annotation = listOfNotNull(number, category).joinToString(" · ").takeIf { it.isNotBlank() }
            val total = row.total.ifBlank { language.text("غير موجود", "Missing") }
            val checkedAt = formatInvoiceCheckTime(row.checkedAt, language)
            InvoiceHistoryRowCard(
                seller = seller, vat = vat, annotation = annotation,
                total = language.text("إجمالي الفاتورة: $total", "Invoice total: $total"),
                status = null, passed = false, checkedAt = language.text("تاريخ الفحص: $checkedAt", "Checked: $checkedAt"),
                onOpen = { onOpen(row.id) }, onDelete = if (readOnly) null else ({ onDeleteInvoice(row) }),
                deleteDescription = language.text("حذف فاتورة $seller", "Delete invoice from $seller"),
                deleteEnabled = actionsEnabled, modifier = Modifier.testTag("invoice-history-row"),
            )
        }
        if (loadFailed) {
            item {
                Column {
                    Text(language.text("تعذّر تحميل السجل", "Could not load invoice history"))
                    TextButton(onClick = ::loadMore) { Text(language.text("إعادة المحاولة", "Retry")) }
                }
            }
        } else if (page?.hasMore == true) {
            item { TextButton(onClick = ::loadMore) { Text(language.text("عرض المزيد", "Show more")) } }
        }
    }
}
