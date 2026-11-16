package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun InvoiceAnnotationCard(
    fields: InvoiceAnnotationFields,
    labels: InvoiceAnnotationLabels,
    categoryLabel: @Composable (Int) -> String,
    editLabel: String,
    editDescription: String,
    onSave: (InvoiceAnnotationFields) -> Boolean,
    saveError: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(labels.title, style = MaterialTheme.typography.titleSmall)
                val number = fields.number.takeIf { it.isNotBlank() }?.let { "${labels.number}: $it" }
                val category = if (fields.category != 0) categoryLabel(fields.category) else null
                if (number != null && category != null) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val combined = "$number · $category"
                        val fits = rememberTextMeasurer().measure(combined, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, softWrap = false).size.width <= with(LocalDensity.current) { maxWidth.roundToPx() }
                        if (fits) AnnotationLine(combined, "invoice-saved-number")
                        else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AnnotationLine(number, "invoice-saved-number")
                            AnnotationLine(category, "invoice-saved-category")
                        }
                    }
                } else {
                    number?.let { AnnotationLine(it, "invoice-saved-number") }
                    category?.let { AnnotationLine(it, "invoice-saved-category") }
                }
                if (fields.note.isNotBlank()) {
                    Text(fields.note, Modifier.testTag("invoice-saved-note"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { editing = true },
                modifier = Modifier.testTag("invoice-annotation-edit").semantics { contentDescription = editDescription }) {
                Text(editLabel)
            }
        }
    }
    if (editing) InvoiceAnnotationDialog(
        initial = fields, labels = labels, categoryLabel = categoryLabel,
        onDismiss = { editing = false }, onSave = onSave, saveError = saveError,
    )
}

@Composable
private fun AnnotationLine(text: String, tag: String) {
    Text(text, Modifier.testTag(tag), maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall)
}
