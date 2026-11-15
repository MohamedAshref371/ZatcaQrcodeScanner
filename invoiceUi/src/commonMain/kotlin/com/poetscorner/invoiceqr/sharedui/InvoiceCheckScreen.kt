package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.ParsedInvoice
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser

private val lightColors = lightColorScheme(
    primary = Color(0xFF116447),
    onPrimary = Color.White,
    background = Color(0xFFF4F8F5),
    onBackground = Color(0xFF17251E),
    surface = Color.White,
    onSurface = Color(0xFF17251E),
    onSurfaceVariant = Color(0xFF43534A),
    outlineVariant = Color(0xFFD4E0D7),
    primaryContainer = Color(0xFFDCEFE4),
    onPrimaryContainer = Color(0xFF11412E),
    error = Color(0xFF9D322A),
    errorContainer = Color(0xFFFCEAE6),
    onErrorContainer = Color(0xFF70261F),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFA7DBBB),
    onPrimary = Color(0xFF073821),
    background = Color(0xFF101A15),
    onBackground = Color(0xFFE5EFE7),
    surface = Color(0xFF1A2820),
    onSurface = Color(0xFFE5EFE7),
    onSurfaceVariant = Color(0xFFBACBBF),
    outlineVariant = Color(0xFF3A5143),
    primaryContainer = Color(0xFF214D36),
    onPrimaryContainer = Color(0xFFD2EDDC),
)

private val invoiceFields = listOf(
    1 to "اسم البائع",
    2 to "الرقم الضريبي",
    3 to "تاريخ الفاتورة",
    4 to "الإجمالي شامل الضريبة",
    5 to "ضريبة القيمة المضافة",
)

@Composable
fun InvoiceCheckScreen() {
    var input by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<ParsedInvoice?>(null) }
    val focus = LocalFocusManager.current

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("قارئ فاتورة", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text("فحص رمز الفاتورة", style = MaterialTheme.typography.headlineSmall)
                            Text("الصق محتوى رمز QR بصيغة Base64.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                result = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("invoice-input"),
                            label = { Text("محتوى Base64") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = TextDirection.Ltr,
                            ),
                            minLines = 2,
                            maxLines = 3,
                            isError = result?.error != null,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = {
                                    focus.clearFocus(force = true)
                                    result = ZatcaInvoiceParser.parse(input)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("invoice-submit"),
                                enabled = input.isNotBlank(),
                            ) {
                                Text("فحص الفاتورة")
                            }
                            TextButton(
                                onClick = {
                                    input = ""
                                    result = null
                                },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("invoice-clear"),
                                enabled = input.isNotEmpty(),
                            ) {
                                Text("مسح")
                            }
                        }

                        result?.let { InvoiceResult(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceResult(result: ParsedInvoice) {
    val colors = MaterialTheme.colorScheme
    if (result.error != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.errorContainer,
            contentColor = colors.onErrorContainer,
        ) {
            Text(
                "تعذّر قراءة رمز الفاتورة. تأكد من نسخ محتوى Base64 كاملًا.",
                modifier = Modifier.padding(16.dp).testTag("invoice-error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("بيانات الفاتورة", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("invoice-result"))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (result.passed) colors.primaryContainer else colors.errorContainer,
                contentColor = if (result.passed) colors.onPrimaryContainer else colors.onErrorContainer,
            ) {
                Text(
                    if (result.passed) "اجتازت الفحص المحلي" else "بعض الحقول تحتاج مراجعة",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("invoice-status").semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            invoiceFields.forEachIndexed { index, (tag, label) ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                    Text(
                        result.value(tag).ifBlank { "—" },
                        Modifier.testTag("invoice-field-$tag"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = if (tag == 1) TextDirection.Content else TextDirection.Ltr,
                        ),
                    )
                }
            }
        }
    }
}
