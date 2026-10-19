package com.poetscorner.zatcaqrcodescanner.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.poetscorner.zatcaqrcodescanner.BuildConfig
import com.poetscorner.zatcaqrcodescanner.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poetscorner.zatcaqrcodescanner.data.ScannerSettings
import com.poetscorner.zatcaqrcodescanner.domain.Invoice
import com.poetscorner.zatcaqrcodescanner.domain.InvoiceType
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser
import com.poetscorner.zatcaqrcodescanner.ui.theme.ZatcaQrcodeScannerTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerApp(vm: ScannerViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("الماسح") }
    var clearDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::gallery) }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.export(uri, false) } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> vm.export(uri, true) } }
    LaunchedEffect(result) { if (result != null) screen = "النتيجة" }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    BackHandler(screen != "الماسح") { vm.result.value = null; screen = "الماسح" }
    ZatcaQrcodeScannerTheme(darkTheme = when(settings.theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }) {
        Scaffold(topBar = { TopAppBar(title = { Column {
            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
            Text("ZATCA · $screen", style = MaterialTheme.typography.labelMedium)
        } }) }, bottomBar = { NavigationBar {
            listOf("الماسح", "النتيجة", "السجل", "الإعدادات").forEachIndexed { index, label ->
                NavigationBarItem(selected = screen == label, onClick = {
                    if (label == "الماسح") vm.result.value = null
                    screen = label
                }, icon = { Text(listOf("▣", "✓", "≡", "⚙")[index]) }, label = { Text(label) })
            }
        } }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(screen) {
                    "الماسح" -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("قراءة محلية • QR فقط", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                            TextButton(onClick = { gallery.launch("image/*") }, enabled = !busy) { Text("صورة من المعرض") }
                        }
                        if (!busy) CameraScanner(settings, vm::scanned) { vm.update { it.copy(torch = !it.torch) } }
                    }
                    "النتيجة" -> if (result == null) EmptyState("لم تُقرأ فاتورة بعد") else ResultScreen(result!!) {
                        vm.result.value = null; screen = "الماسح"
                    }
                    "السجل" -> {
                        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { json.launch("zatca-history.json") }) { Text("تصدير JSON") }
                            TextButton(onClick = { csv.launch("zatca-history.csv") }) { Text("تصدير CSV") }
                            TextButton(onClick = { clearDialog = true }, enabled = history.isNotEmpty()) { Text("مسح") }
                        }
                        if (history.isEmpty()) EmptyState("لا توجد قراءات محفوظة")
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(history, key = { it.id }) { row ->
                                Card(Modifier.fillMaxWidth().clickable { vm.open(row); screen = "النتيجة" }) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(row.seller.ifBlank { "رمز غير صالح / بلا اسم" }, fontWeight = FontWeight.Bold)
                                        Text(if (row.invoiceType == InvoiceType.SIMPLIFIED.name) InvoiceType.SIMPLIFIED.label else InvoiceType.STANDARD.label)
                                        Text(DateFormat.getDateTimeInstance().format(Date(row.scannedAt)), style = MaterialTheme.typography.bodySmall)
                                        Text(if(row.passed) "اجتاز الفحوص المحلية · المرحلة ${row.phase}" else "لم يجتز الفحوص المحلية", color = if(row.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    else -> SettingsScreen(settings, vm::update)
                }
            }
        }
        if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("مسح السجل؟") }, text = { Text("ستُحذف كل القراءات المحفوظة على هذا الجهاز.") },
            confirmButton = { TextButton(onClick = { vm.clear(); clearDialog = false }) { Text("حذف الكل") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("إلغاء") } })
    }
}

@Composable
private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(32.dp)) { Text(text) } }

@Composable
private fun ResultScreen(invoice: Invoice, again: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(colors = CardDefaults.cardColors(containerColor = if(invoice.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(invoice.passed) "اجتاز فحوص QR المحلية" else "لم يجتز فحوص QR المحلية", style = MaterialTheme.typography.titleLarge)
                Text("${invoice.type.label} · ${if (invoice.phase2) "المرحلة الثانية" else "المرحلة الأولى"}")
                invoice.error?.let { Text(it) }
            }
        } }
        item { Text("الفحص محلي وبنيوي. وجود Hash والتوقيع والمفتاح والختم لا يثبت صحة التوقيع أو اعتماد الفاتورة لدى الهيئة.", style = MaterialTheme.typography.bodySmall) }
        item {
            Text("المرحلة الأولى: ${if (invoice.error == null && invoice.checks.filter { it.phase == 1 }.all { it.passed }) "اجتازت الفحوص" else "لم تجتز"}")
            Text("المرحلة الثانية: ${if (invoice.error == null && invoice.checks.all { it.passed }) "اجتازت الفحوص البنيوية" else "لم تجتز"}")
        }
        items((1..9).toList()) { tag ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("$tag · ${ZatcaParser.names[tag]}", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(invoice.value(tag).ifBlank {
                    if (tag == 9 && invoice.type == InvoiceType.STANDARD) "غير موجود · اختياري للفاتورة الضريبية" else "غير موجود"
                }) }
                if(tag in 6..7) Text("نص Base64 الأصلي", style = MaterialTheme.typography.labelSmall)
                if(tag >= 8) Text("عرض البيانات الثنائية بصيغة Base64", style = MaterialTheme.typography.labelSmall)
            } }
        }
        item { Text("تفاصيل الفحص", style = MaterialTheme.typography.titleMedium) }
        items(invoice.checks) { check -> Text("${if(check.passed) "✓" else "✕"} ${check.label} · م${check.phase}", color = if(check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        item { Text("فحوص المرحلة الثانية معروضة حتى عند غياب حقولها؛ تدخل في الشارة عند وجود أي حقل من 6 إلى 9.", style = MaterialTheme.typography.bodySmall) }
        item { Button(onClick = again, modifier = Modifier.fillMaxWidth()) { Text("مسح فاتورة أخرى") } }
        item { Text("محتوى QR الأصلي", fontWeight = FontWeight.Bold); SelectionContainer { Text(invoice.raw, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun SettingsScreen(s: ScannerSettings, update: ((ScannerSettings) -> ScannerSettings) -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("خيارات محرك القراءة", style = MaterialTheme.typography.titleLarge) }
        item { Toggle("Try harder · بحث مكثّف", s.harder) { v -> update { it.copy(harder = v) } } }
        item { Toggle("Try denoise · تقليل التشويش", s.denoise) { v -> update { it.copy(denoise = v) } } }
        item { Toggle("Try rotate · دوران ±90°", s.rotate) { v -> update { it.copy(rotate = v) } } }
        item { Toggle("Try inverted · ألوان معكوسة", s.inverted) { v -> update { it.copy(inverted = v) } } }
        item { Choice("Binarizer", s.binarizer, listOf("LOCAL_AVERAGE" to "Local average", "GLOBAL_HISTOGRAM" to "Global histogram", "FIXED_THRESHOLD" to "Fixed threshold", "BOOL_CAST" to "Bool cast")) { v -> update { it.copy(binarizer = v) } } }
        item { Choice("دقة المسح", s.resolution, listOf("HD" to "HD · 720p", "FHD" to "FHD · 1080p", "QHD" to "QHD · 1440p")) { v -> update { it.copy(resolution = v) } } }
        item { Choice("تردد المسح", s.speed, listOf("NORMAL" to "عادي · كل 300ms", "FAST" to "سريع · كل 100ms", "FASTEST" to "الأسرع · كل إطار متاح")) { v -> update { it.copy(speed = v) } } }
        item { Toggle("تشغيل الكشاف", s.torch) { v -> update { it.copy(torch = v) } } }
        item { Toggle("التركيز بالضغط", s.tapFocus) { v -> update { it.copy(tapFocus = v) } } }
        item { Choice("المظهر", s.theme, listOf("SYSTEM" to "حسب النظام", "LIGHT" to "فاتح", "DARK" to "داكن")) { v -> update { it.copy(theme = v) } } }
        item { Text("الدقة الفعلية حسب الكاميرا. الخيارات المكثفة والدقة العالية قد تزيد استهلاك البطارية ووقت القراءة. الزوم متاح أسفل الكاميرا.", style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(); TextButton(onClick = { showAbout = true }) { Text(stringResource(R.string.about_app)) } }
    }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false },
        title = { Text(stringResource(R.string.app_name)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            Text(stringResource(R.string.about_details))
        } }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text("إغلاق") } })
}
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(top = 12.dp)); Switch(checked, onCheckedChange = onChange)
    }
}
@Composable
private fun Choice(label: String, selected: String, values: List<Pair<String,String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(values.firstOrNull { it.first == selected }?.second ?: selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}
