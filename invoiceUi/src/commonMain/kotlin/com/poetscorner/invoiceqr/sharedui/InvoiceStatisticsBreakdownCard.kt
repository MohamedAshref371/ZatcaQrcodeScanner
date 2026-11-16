package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class InvoiceBreakdownAmounts(val originalTotal: String, val netTotal: String, val originalTax: String, val netTax: String)
data class InvoiceBreakdownSection(val title: String, val amounts: InvoiceBreakdownAmounts, val notices: List<String>)

@Composable
fun InvoiceStatisticsBreakdownCard(title: String, count: String, originalLabel: String, contributionLabel: String,
    totalLabel: String, taxLabel: String, amounts: InvoiceBreakdownAmounts, sections: List<InvoiceBreakdownSection>) {
    @Composable fun row(label: String, original: String, net: String, emphasized: Boolean) {
        val style = if (emphasized) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, Modifier.weight(1.15f), style = style)
            Text(original, Modifier.weight(1f), style = style, textAlign = TextAlign.Center)
            Text(net, Modifier.weight(1f), style = style, textAlign = TextAlign.Center,
                fontWeight = if (emphasized) FontWeight.SemiBold else null)
        }
    }
    @Composable fun amountRows(value: InvoiceBreakdownAmounts, emphasized: Boolean) {
        row(totalLabel, value.originalTotal, value.netTotal, emphasized)
        row(taxLabel, value.originalTax, value.netTax, emphasized)
    }
    Card(Modifier.fillMaxWidth().testTag("statistics_breakdown"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(count, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1.15f))
                Text(originalLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Text(contributionLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
            amountRows(amounts, true)
            sections.forEach { section ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(section.title, style = MaterialTheme.typography.titleSmall)
                amountRows(section.amounts, false)
                section.notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
