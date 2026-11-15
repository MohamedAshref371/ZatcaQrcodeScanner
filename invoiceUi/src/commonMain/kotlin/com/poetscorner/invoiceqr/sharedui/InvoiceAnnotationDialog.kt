package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.InvoiceAnnotationRules

data class InvoiceAnnotationFields(
    val number: String = "",
    val category: Int = InvoiceAnnotationRules.NO_CATEGORY,
    val note: String = "",
)

data class InvoiceAnnotationLabels(
    val title: String,
    val number: String,
    val category: String,
    val note: String,
    val save: String,
    val cancel: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceAnnotationDialog(
    initial: InvoiceAnnotationFields,
    labels: InvoiceAnnotationLabels,
    categoryLabel: @Composable (Int) -> String,
    onDismiss: () -> Unit,
    onSave: (InvoiceAnnotationFields) -> Boolean,
    saveError: String? = null,
) {
    var number by rememberSaveable { mutableStateOf(initial.number) }
    var category by rememberSaveable { mutableIntStateOf(initial.category) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var expanded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(number, { number = it.take(InvoiceAnnotationRules.MAX_NUMBER) },
                    label = { Text(labels.number) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("invoice-annotation-number"))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = categoryLabel(category), onValueChange = {}, readOnly = true,
                        label = { Text(labels.category) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().testTag("invoice_annotation_category")
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        InvoiceAnnotationRules.categoryIds.forEach { id ->
                            DropdownMenuItem(text = { Text(categoryLabel(id)) },
                                onClick = { category = id; expanded = false },
                                modifier = Modifier.testTag("invoice-annotation-category-$id")
                                    .semantics { selected = category == id })
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(InvoiceAnnotationRules.MAX_NOTE) },
                    label = { Text(labels.note) }, minLines = 3, maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("invoice-annotation-note"))
                if (failed && saveError != null) Text(saveError, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val fields = InvoiceAnnotationFields(number.trim(), category, note.trim())
                failed = !onSave(fields)
                if (!failed) onDismiss()
            }, modifier = Modifier.testTag("invoice-annotation-save")) { Text(labels.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } },
    )
}
