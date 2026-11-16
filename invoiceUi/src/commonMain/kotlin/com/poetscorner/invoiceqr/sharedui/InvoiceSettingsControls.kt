package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun InvoiceSettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked, onCheckedChange = null)
        }
    }
}
@Composable
fun InvoiceSettingChoice(label: String, selected: String, values: List<Pair<String,String>>,
    singleLine: Boolean = false, border: BorderStroke? = null, equalWidth: Boolean = false,
    warningDescription: String? = null,
    onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val selectedLabel = values.firstOrNull { it.first == selected }?.second ?: selected
    Box {
        Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium, border = border,
            modifier = Modifier.semantics { stateDescription = selectedLabel }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (largeText) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            if (warningDescription != null) Icon(InvoiceWarningIcon,
                                modifier = Modifier.size(20.dp), contentDescription = warningDescription,
                                tint = Color(0xFFF9A825))
                        }
                        Text(selectedLabel, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                } else if (equalWidth) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(selectedLabel,
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(InvoiceExpandIcon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (singleLine) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (warningDescription != null) {
                            Icon(InvoiceWarningIcon, modifier = Modifier.size(20.dp),
                                contentDescription = warningDescription, tint = Color(0xFFF9A825))
                        }
                    }
                    Text(selectedLabel,
                        if (warningDescription != null) Modifier else Modifier.weight(1.35f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(selectedLabel,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (!equalWidth) Icon(InvoiceExpandIcon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) },
                onClick = { onChange(key); expanded = false },
                modifier = Modifier.semantics { this.selected = key == selected }) }
        }
    }
}
