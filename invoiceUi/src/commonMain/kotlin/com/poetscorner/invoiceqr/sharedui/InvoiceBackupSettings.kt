package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class InvoiceBackupStatus { EXPORTED, IMPORTED, CANCELLED, FAILED, INVALID, OVERSIZED }

data class InvoiceBackupResult(val status: InvoiceBackupStatus, val added: Int = 0, val duplicates: Int = 0)

fun interface InvoiceBackupObserver {
    fun complete(result: InvoiceBackupResult)
}

interface InvoiceBackupActions {
    fun exportBackup(observer: InvoiceBackupObserver)
    fun importBackup(observer: InvoiceBackupObserver)
}

internal fun InvoiceBackupResult.message(): String? = when (status) {
    InvoiceBackupStatus.EXPORTED -> "تم حفظ النسخة الاحتياطية"
    InvoiceBackupStatus.IMPORTED -> "تمت إضافة $added فاتورة. الفواتير المكررة: $duplicates"
    InvoiceBackupStatus.CANCELLED -> null
    InvoiceBackupStatus.FAILED -> "تعذّر نقل النسخة الاحتياطية. حاول مرة أخرى."
    InvoiceBackupStatus.INVALID -> "ملف النسخة الاحتياطية تالف أو بصيغة غير مدعومة."
    InvoiceBackupStatus.OVERSIZED -> "ملف النسخة الاحتياطية يتجاوز 64 ميبي بايت."
}

@Composable
internal fun InvoiceBackupSettings(busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.titleLarge)
        InvoiceFieldCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("النسخ الاحتياطي والاستعادة", style = MaterialTheme.typography.titleMedium)
                Text("احفظ سجل الفواتير في ملف JSON متوافق مع Android. ملف النسخة الاحتياطية غير مشفّر.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("invoice-backup-export")) {
                    Text("إنشاء نسخة احتياطية")
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("invoice-backup-import")) {
                    Text("استعادة من نسخة احتياطية")
                }
                if (busy) CircularProgressIndicator()
            }
        }
    }
}
