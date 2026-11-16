package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class InvoiceResultField(val tag: Int, val label: String, val value: String, val hasValue: Boolean,
    val dateLines: List<String> = emptyList(), val footer: String? = null)

data class InvoiceResultCheck(val passed: Boolean, val text: String)
data class InvoiceVerificationRow(val label: String, val status: String, val failed: Boolean = false)

data class InvoiceResultLabels(
    val status: String, val summary: String, val checkedAt: String, val disclaimer: String,
    val fields: String, val share: String, val copyVat: String, val again: String,
    val showAdvanced: String, val hideAdvanced: String, val showChecks: String, val hideChecks: String,
    val showVerification: String, val hideVerification: String, val showRaw: String, val hideRaw: String,
)

@Composable
fun InvoiceResultView(
    raw: String,
    passed: Boolean,
    phaseTwo: Boolean,
    labels: InvoiceResultLabels,
    fields: List<InvoiceResultField>,
    checks: List<InvoiceResultCheck>,
    verification: List<InvoiceVerificationRow>,
    reports: List<String>,
    error: String?,
    reviewHint: String?,
    onShare: (() -> Unit)?,
    onCopyVat: (String) -> Unit,
    onAgain: () -> Unit,
    listTag: String,
    annotations: (@Composable () -> Unit)? = null,
    rawContent: @Composable () -> Unit,
) {
    var advanced by rememberSaveable(raw) { mutableStateOf(false) }
    var showChecks by rememberSaveable(raw) { mutableStateOf(false) }
    var showRaw by rememberSaveable(raw) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize().testTag(listTag), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InvoiceSummaryCard(passed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.size(44.dp), color = colors.surfaceContainerLowest, shape = MaterialTheme.shapes.small) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (passed) CheckIcon else InfoIcon, null, Modifier.size(24.dp),
                                tint = if (passed) colors.primary else colors.error)
                        }
                    }
                    Text(labels.status, Modifier.weight(1f).testTag("invoice-status"), style = MaterialTheme.typography.titleMedium)
                    if (onShare != null) IconButton(onClick = onShare, modifier = Modifier.testTag("invoice-share")) {
                        Icon(ShareIcon, labels.share, Modifier.size(24.dp))
                    }
                }
                Text(labels.summary)
                error?.let { Text(it) }
                if (!passed && reviewHint != null) Text(reviewHint, style = MaterialTheme.typography.bodyMedium)
                checks.filterNot { it.passed }.forEach { Text(it.text, style = MaterialTheme.typography.bodySmall) }
                Text(labels.checkedAt, Modifier.testTag("invoice-checked-at"), style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text(labels.disclaimer, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        if (annotations != null) item { annotations() }
        item {
            Text(labels.fields, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).testTag("invoice-result"),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(fields.filter { it.tag <= 6 }, key = { it.tag }) { InvoiceResultFieldCard(it, labels.copyVat, onCopyVat) }
        if (phaseTwo) {
            item {
                OutlinedButton(onClick = { advanced = !advanced },
                    modifier = Modifier.fillMaxWidth().testTag("invoice-advanced")) {
                    Text(if (advanced) labels.hideAdvanced else labels.showAdvanced)
                }
            }
            if (advanced) items(fields.filter { it.tag > 6 }, key = { it.tag }) { InvoiceResultFieldCard(it, labels.copyVat, onCopyVat) }
        }
        item { InvoiceVerificationCard(raw, labels.showVerification, labels.hideVerification, verification) }
        item {
            TextButton(onClick = { showChecks = !showChecks }, modifier = Modifier.fillMaxWidth().testTag("invoice-checks")) {
                Text(if (showChecks) labels.hideChecks else labels.showChecks)
            }
        }
        if (showChecks) {
            item {
                Column(Modifier.fillMaxWidth().testTag("invoice-check-details")) {
                    reports.forEach { Text(it) }
                    checks.forEach { Text(it.text, color = if (it.passed) colors.primary else colors.error) }
                }
            }
        }
        item {
            TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.fillMaxWidth().testTag("invoice-raw")) {
                Text(if (showRaw) labels.hideRaw else labels.showRaw)
            }
        }
        if (showRaw) item { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { rawContent() } }
        item { Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text(labels.again) } }
    }
}

@Composable
fun InvoiceVerificationCard(raw: String, showLabel: String, hideLabel: String, rows: List<InvoiceVerificationRow>) {
    var expanded by rememberSaveable(raw) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("invoice-levels")) {
        Text(if (expanded) hideLabel else showLabel)
    }
    if (!expanded) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(row.status, style = MaterialTheme.typography.bodySmall,
                        color = if (row.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InvoiceResultFieldCard(field: InvoiceResultField, copyLabel: String, onCopyVat: (String) -> Unit) {
    if (field.tag == 3) {
        InvoiceFieldCard {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InvoiceResultFieldLabel(field.label)
                    field.dateLines.forEachIndexed { index, text ->
                        Text(text, if (index == 0) Modifier.testTag("invoice-field-3") else Modifier,
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        return
    }
    InvoiceFieldCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InvoiceResultFieldLabel(field.label)
            val direction = if (field.tag == 2) LayoutDirection.Ltr else LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer(Modifier.weight(1f)) {
                        val style = if (field.tag == 4) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge
                        Text(field.value, Modifier.testTag("invoice-field-${field.tag}"),
                            style = if (field.tag >= 2 && field.hasValue) style.copy(textDirection = TextDirection.Ltr) else style,
                            fontWeight = if (field.tag == 1 || field.tag == 4) FontWeight.Bold else FontWeight.Normal)
                    }
                    if (field.tag == 2) IconButton(onClick = { onCopyVat(field.value) }, enabled = field.hasValue) {
                        Icon(CopyIcon, copyLabel, Modifier.size(24.dp))
                    }
                }
            }
            field.footer?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun InvoiceResultFieldLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
