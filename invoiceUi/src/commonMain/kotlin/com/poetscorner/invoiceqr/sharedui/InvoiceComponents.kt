package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable
fun InvoiceManualEntry(
    busy: Boolean,
    title: String,
    hint: String,
    fieldLabel: String,
    buttonLabel: String,
    icon: @Composable () -> Unit,
    onInspect: (String) -> Unit,
) {
    var raw by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier.fillMaxWidth().testTag("invoice-input"),
            label = { Text(fieldLabel) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            minLines = 4,
            maxLines = 8,
            shape = MaterialTheme.shapes.medium,
            enabled = !busy,
        )
        Button(
            onClick = { keyboard?.hide(); onInspect(raw.trim()) },
            enabled = raw.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().testTag("invoice-submit"),
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
fun InvoiceSummaryCard(passed: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = if (passed) colors.primaryContainer else colors.errorContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun InvoiceFieldCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        content = content,
    )
}
