package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role

enum class InvoiceBackupStatus { EXPORTED, IMPORTED, CANCELLED, FAILED, INVALID, OVERSIZED }

data class InvoiceBackupResult(val status: InvoiceBackupStatus, val added: Int = 0, val duplicates: Int = 0)

fun interface InvoiceBackupObserver {
    fun complete(result: InvoiceBackupResult)
}

interface InvoiceBackupActions {
    fun exportBackup(observer: InvoiceBackupObserver)
    fun importBackup(observer: InvoiceBackupObserver)
}

internal fun InvoiceBackupResult.message(language: InvoiceLanguage): String? = when (status) {
    InvoiceBackupStatus.EXPORTED -> language.text("تم حفظ النسخة الاحتياطية", "Backup saved")
    InvoiceBackupStatus.IMPORTED -> language.text("تمت إضافة $added فاتورة. الفواتير المكررة: $duplicates", "Invoices added: $added. Duplicates: $duplicates.")
    InvoiceBackupStatus.CANCELLED -> null
    InvoiceBackupStatus.FAILED -> language.text("تعذّر نقل النسخة الاحتياطية. حاول مرة أخرى.", "Could not transfer the backup. Try again.")
    InvoiceBackupStatus.INVALID -> language.text("ملف النسخة الاحتياطية تالف أو بصيغة غير مدعومة.", "The backup file is invalid or uses an unsupported format.")
    InvoiceBackupStatus.OVERSIZED -> language.text("ملف النسخة الاحتياطية يتجاوز 64 ميبي بايت.", "The backup file exceeds 64 MiB.")
}

@Composable
internal fun InvoiceBackupSettings(
    busy: Boolean,
    languagePreference: InvoiceLanguagePreference,
    onLanguageChange: ((InvoiceLanguagePreference) -> Unit)?,
    scannerOptions: @Composable () -> Unit = {},
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val language = LocalInvoiceLanguage.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(language.text("الإعدادات", "Settings"), style = MaterialTheme.typography.titleLarge)
        scannerOptions()
        if (onLanguageChange != null) InvoiceFieldCard {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(language.text("لغة التطبيق", "App language"), style = MaterialTheme.typography.titleMedium)
                InvoiceLanguagePreference.entries.forEach { option ->
                    val selected = languagePreference == option
                    Row(
                        Modifier.fillMaxWidth().selectable(selected, enabled = !busy, role = Role.RadioButton,
                            onClick = { onLanguageChange(option) })
                            .testTag("invoice-language-${option.name.lowercase()}").padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected, onClick = null, enabled = !busy)
                        Text(when (option) {
                            InvoiceLanguagePreference.DEVICE -> language.text("لغة الجهاز", "Device language")
                            InvoiceLanguagePreference.ARABIC -> "العربية"
                            InvoiceLanguagePreference.ENGLISH -> "English"
                        })
                    }
                }
            }
        }
        InvoiceFieldCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(language.text("النسخ الاحتياطي والاستعادة", "Backup and restore"), style = MaterialTheme.typography.titleMedium)
                Text(language.text("احفظ سجل الفواتير في ملف JSON متوافق مع Android. ملف النسخة الاحتياطية غير مشفّر.", "Save invoice history to a JSON file compatible with Android. The backup file is unencrypted."),
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("invoice-backup-export")) {
                    Text(language.text("إنشاء نسخة احتياطية", "Create backup"))
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("invoice-backup-import")) {
                    Text(language.text("استعادة من نسخة احتياطية", "Restore from backup"))
                }
                if (busy) CircularProgressIndicator()
            }
        }
    }
}

@Composable
internal fun InvoiceScannerOptions(engine: InvoiceQrEngine, mlKitAvailable: Boolean, cropEnabled: Boolean,
    onEngine: (InvoiceQrEngine) -> Unit, onCrop: (Boolean) -> Unit) {
    val language = LocalInvoiceLanguage.current
    InvoiceFieldCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(language.text("محرك القراءة", "Scan engine"), style = MaterialTheme.typography.titleMedium)
            InvoiceQrEngine.entries.forEach { option ->
                val enabled = option != InvoiceQrEngine.ML_KIT || mlKitAvailable
                Row(Modifier.fillMaxWidth().selectable(engine == option, enabled = enabled, role = Role.RadioButton,
                    onClick = { onEngine(option) }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = engine == option, onClick = null, enabled = enabled)
                    Text(if (option == InvoiceQrEngine.ZXING) "ZXing" else "Google ML Kit",
                        Modifier.padding(start = 8.dp))
                }
            }
            if (!mlKitAvailable) Text(language.text("Google ML Kit متاح في نسخة الآيفون الفعلي.",
                "Google ML Kit is available in the iPhone device build."), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(language.text("تحديد منطقة المسح", "Crop scan area"), Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = cropEnabled, onCheckedChange = onCrop)
            }
        }
    }
}
