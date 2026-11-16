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
    appearanceOptions: @Composable () -> Unit = {},
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val language = LocalInvoiceLanguage.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(language.text("الإعدادات", "Settings"), Modifier.testTag("invoice-settings-title"), style = MaterialTheme.typography.titleLarge)
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
        appearanceOptions()
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
    onEngine: (InvoiceQrEngine) -> Unit, onCrop: (Boolean) -> Unit, zxingSettings: InvoiceZxingSettings,
    onZxingChange: (InvoiceZxingSettings) -> Unit, scanSpeed: String, onScanSpeed: (String) -> Unit,
    resolution: InvoiceScanResolution, onResolution: (InvoiceScanResolution) -> Unit) {
    val language = LocalInvoiceLanguage.current
    Text(language.text("الكاميرا", "Camera"), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
    InvoiceSettingChoice(
        language.text("دقة الكاميرا", "Camera resolution"), resolution.name,
        InvoiceScanResolution.entries.map { it.name to "${it.name} · ${it.height}p" }, singleLine = true,
        warningDescription = when (resolution) {
            InvoiceScanResolution.SD -> language.text("قد تصعّب دقة 480p قراءة رموز QR الصغيرة", "480p may make small QR codes harder to read")
            InvoiceScanResolution.UHD -> language.text("قد تبطئ دقة 2160p المسح وتستهلك بطارية أكثر", "2160p may slow scanning and use more battery")
            else -> null
        },
    ) { onResolution(InvoiceScanResolution.fromPreference(it)) }
    InvoiceSettingChoice(language.text("تكرار المسح", "Scan frequency"), scanSpeed, listOf(
        "NORMAL" to language.text("عادي · كل 300ms", "Normal · every 300 ms"),
        "FAST" to language.text("سريع · كل 100ms", "Fast · every 100 ms"),
        "FASTEST" to language.text("سريع جدا · كل 50ms", "Very fast · every 50ms"),
    ), singleLine = true, onChange = onScanSpeed)
    InvoiceSettingToggle(language.text("تحديد منطقة المسح", "Crop scan area"), cropEnabled, onCrop)
    InvoiceSettingChoice(language.text("محرك القراءة", "Scan engine"), engine.name,
        listOf("ZXING" to "ZXing") + (if (mlKitAvailable) listOf("ML_KIT" to "Google ML Kit") else emptyList()),
        singleLine = true) { onEngine(InvoiceQrEngine.valueOf(it)) }
    if (!mlKitAvailable) Text(language.text("Google ML Kit متاح في نسخة الآيفون الفعلي.",
        "Google ML Kit is available in the iPhone device build."), style = MaterialTheme.typography.bodySmall)
    if (engine == InvoiceQrEngine.ZXING) {
        Text("ZXing", Modifier.testTag("invoice-settings-zxing"), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        InvoiceSettingToggle(language.text("Try harder · بحث مكثّف", "Try harder"), zxingSettings.harder) {
            onZxingChange(zxingSettings.copy(harder = it))
        }
        InvoiceSettingToggle(language.text("Try denoise · إزالة التشويش", "Try denoise"), zxingSettings.denoise) {
            onZxingChange(zxingSettings.copy(denoise = it))
        }
        InvoiceSettingToggle(language.text("Try rotate · دوران ±90°", "Try rotate · ±90°"), zxingSettings.rotate) {
            onZxingChange(zxingSettings.copy(rotate = it))
        }
        InvoiceSettingToggle(language.text("Try inverted · ألوان معكوسة", "Try inverted"), zxingSettings.inverted) {
            onZxingChange(zxingSettings.copy(inverted = it))
        }
        InvoiceSettingChoice(language.text("Binarizer · التحويل الثنائي", "Binarizer"), zxingSettings.binarizer, listOf(
            "LOCAL_AVERAGE" to language.text("المتوسط المحلي (Local average)", "Local average"),
            "GLOBAL_HISTOGRAM" to language.text("توزيع الإضاءة العام (Global histogram)", "Global histogram"),
            "FIXED_THRESHOLD" to language.text("حد فصل ثابت (Fixed threshold)", "Fixed threshold"),
            "BOOL_CAST" to language.text("فصل عند الصفر (Bool cast)", "Bool cast"),
        )) { onZxingChange(zxingSettings.copy(binarizer = it)) }
        InvoiceSettingToggle(
            language.text("استعادة رموز QR من عدة إطارات", "Recover QR codes from multiple frames"),
            zxingSettings.multiFrameRecovery,
        ) { onZxingChange(zxingSettings.copy(multiFrameRecovery = it)) }
    }
}

@Composable
internal fun InvoiceAppearanceOptions(mode: String, color: String, onMode: (String) -> Unit, onColor: (String) -> Unit) {
    val language = LocalInvoiceLanguage.current
    Text(language.text("المظهر", "Appearance"), Modifier.testTag("invoice-settings-appearance"),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    InvoiceSettingChoice(language.text("ألوان التطبيق", "App theme"), color, listOf(
        "DEFAULT" to language.text("الافتراضي", "Default"), "GOLD" to language.text("ذهبي", "Gold"),
        "PURPLE" to language.text("بنفسجي", "Purple"), "BLUE" to language.text("أزرق", "Blue"),
    ), singleLine = true, onChange = onColor)
    InvoiceSettingChoice(language.text("المظهر", "Appearance"), mode, listOf(
        "SYSTEM" to language.text("حسب الجهاز", "System"), "LIGHT" to language.text("فاتح", "Light"),
        "DARK" to language.text("داكن", "Dark"),
    ), singleLine = true, onChange = onMode)
}
