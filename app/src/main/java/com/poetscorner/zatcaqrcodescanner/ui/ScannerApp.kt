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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.poetscorner.zatcaqrcodescanner.BuildConfig
import com.poetscorner.zatcaqrcodescanner.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.poetscorner.zatcaqrcodescanner.billing.SupporterViewModel
import com.poetscorner.zatcaqrcodescanner.billing.SupporterState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poetscorner.zatcaqrcodescanner.data.ScannerSettings
import com.poetscorner.zatcaqrcodescanner.data.ScanEngine
import com.poetscorner.zatcaqrcodescanner.domain.Invoice
import com.poetscorner.zatcaqrcodescanner.domain.InvoiceType
import com.poetscorner.zatcaqrcodescanner.domain.ZatcaParser
import com.poetscorner.zatcaqrcodescanner.ui.theme.ZatcaQrcodeScannerTheme
import java.text.DateFormat
import java.util.Date
import com.poetscorner.zatcaqrcodescanner.ui.text.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerApp(vm: ScannerViewModel = viewModel(), supporter: SupporterViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val support by supporter.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { supporter.refresh() }
    var screen by rememberSaveable { mutableIntStateOf(0) }
    var clearDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::gallery) }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.export(uri, false) } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> vm.export(uri, true) } }
    LaunchedEffect(result) { if (result != null) screen = 1 }
    LaunchedEffect(message, configuration) { message?.let { snackbar.showSnackbar(it.resolve(context)); vm.message.value = null } }
    BackHandler(screen != 0) { vm.result.value = null; screen = 0 }
    ZatcaQrcodeScannerTheme(darkTheme = when(settings.theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() },
        goldenTheme = support.owned && settings.goldenTheme) {
        Scaffold(topBar = { TopAppBar(title = { Column {
            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.toolbar_subtitle, stringResource(listOf(R.string.nav_scanner, R.string.nav_result, R.string.nav_history, R.string.nav_settings)[screen])), style = MaterialTheme.typography.labelMedium)
        } }) }, bottomBar = { NavigationBar {
            listOf(R.string.nav_scanner, R.string.nav_result, R.string.nav_history, R.string.nav_settings).forEachIndexed { index, label ->
                NavigationBarItem(selected = screen == index, onClick = {
                    if (index == 0) vm.result.value = null
                    screen = index
                }, icon = { Text(listOf("▣", "✓", "≡", "⚙")[index]) }, label = { Text(stringResource(label)) })
            }
        } }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(screen) {
                    0 -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.scan_local), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(top = 16.dp))
                            TextButton(onClick = { gallery.launch("image/*") }, enabled = !busy) { Text(stringResource(R.string.gallery_pick)) }
                        }
                        if (!busy) CameraScanner(settings, vm::scanned) { vm.update { it.copy(torch = !it.torch) } }
                    }
                    1 -> if (result == null) EmptyState(stringResource(R.string.no_invoice)) else ResultScreen(result!!) {
                        vm.result.value = null; screen = 0
                    }
                    2 -> {
                        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { json.launch("zatca-history.json") }) { Text(stringResource(R.string.export_json)) }
                            TextButton(onClick = { csv.launch("zatca-history.csv") }) { Text(stringResource(R.string.export_csv)) }
                            TextButton(onClick = { clearDialog = true }, enabled = history.isNotEmpty()) { Text(stringResource(R.string.clear)) }
                        }
                        if (history.isEmpty()) EmptyState(stringResource(R.string.no_history))
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(history, key = { it.id }) { row ->
                                Card(Modifier.fillMaxWidth().clickable { vm.open(row); screen = 1 }) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(row.seller.ifBlank { stringResource(R.string.unknown_seller) }, fontWeight = FontWeight.Bold)
                                            Text(DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, configuration.locales[0]).format(Date(row.scannedAt)), style = MaterialTheme.typography.bodySmall)
                                            Text(if(row.passed) stringResource(R.string.history_passed, row.phase) else stringResource(R.string.history_failed), color = if(row.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        }
                                        TextButton(onClick = { vm.delete(row) }) {
                                            Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> SettingsScreen(settings, vm::update, support,
                        onPurchase = { context.activity()?.let(supporter::purchase) }, onRestore = supporter::refresh)
                }
            }
        }
        if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text(stringResource(R.string.clear_title)) }, text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.keepLatestDay(); clearDialog = false }) { Text(stringResource(R.string.keep_latest_day)) }
                    TextButton(onClick = { vm.clear(); clearDialog = false }) {
                        Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error)
                    }
                }
            }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(32.dp)) { Text(text) } }

@Composable
private fun ResultScreen(invoice: Invoice, again: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(colors = CardDefaults.cardColors(containerColor = if(invoice.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(invoice.passed) stringResource(R.string.result_passed) else stringResource(R.string.result_failed), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.invoice_summary, stringResource(invoice.type.label), stringResource(if (invoice.phase2) R.string.phase_two else R.string.phase_one)))
                invoice.error?.let { Text(it.localized()) }
            }
        } }
        item { Text(stringResource(R.string.structural_disclaimer), style = MaterialTheme.typography.bodySmall) }
        item {
            Text(stringResource(R.string.phase_one_report, stringResource(if (invoice.error == null && invoice.checks.filter { it.phase == 1 }.all { it.passed }) R.string.checks_passed else R.string.checks_failed)))
            Text(stringResource(R.string.phase_two_report, stringResource(if (invoice.error == null && invoice.checks.all { it.passed }) R.string.structural_passed else R.string.checks_failed)))
        }
        items((1..9).toList()) { tag ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.tag_title, tag, stringResource(ZatcaParser.names.getValue(tag))), fontWeight = FontWeight.Bold)
                SelectionContainer { Text(invoice.value(tag).ifBlank {
                    if (tag == 9 && invoice.type == InvoiceType.STANDARD) stringResource(R.string.stamp_optional) else stringResource(R.string.missing)
                }, style = if (tag >= 2 && invoice.value(tag).isNotBlank()) MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr) else MaterialTheme.typography.bodyLarge) }
                if(tag in 6..7) Text(stringResource(R.string.base64_original), style = MaterialTheme.typography.labelSmall)
                if(tag >= 8) Text(stringResource(R.string.base64_binary), style = MaterialTheme.typography.labelSmall)
            } }
        }
        item { Text(stringResource(R.string.check_details), style = MaterialTheme.typography.titleMedium) }
        items(invoice.checks) { check -> Text(stringResource(R.string.check_status, if(check.passed) "✓" else "✕", check.label.localized(), check.phase), color = if(check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        item { Text(stringResource(R.string.phase_explanation), style = MaterialTheme.typography.bodySmall) }
        item { Button(onClick = again, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_again)) } }
        item {
            Text(stringResource(R.string.raw_qr), fontWeight = FontWeight.Bold)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                RawQrText(invoice.raw, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsScreen(s: ScannerSettings, update: ((ScannerSettings) -> ScannerSettings) -> Unit,
    support: SupporterState, onPurchase: () -> Unit, onRestore: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SupporterCard(support, s.goldenTheme, onPurchase, onRestore) { value -> update { it.copy(goldenTheme = value) } } }
        item { Text(stringResource(R.string.reader_options), style = MaterialTheme.typography.titleLarge) }
        item { Choice(stringResource(R.string.scan_engine), s.scanEngine.name,
            listOf(ScanEngine.ZXING.name to stringResource(R.string.engine_zxing),
                ScanEngine.ML_KIT.name to stringResource(R.string.engine_ml_kit))) { v ->
            update { it.copy(scanEngine = ScanEngine.fromPreference(v)) }
        } }
        if (s.scanEngine == ScanEngine.ZXING) {
            item { Toggle(stringResource(R.string.try_harder), s.harder) { v -> update { it.copy(harder = v) } } }
            item { Toggle(stringResource(R.string.try_denoise), s.denoise) { v -> update { it.copy(denoise = v) } } }
            item { Toggle(stringResource(R.string.try_rotate), s.rotate) { v -> update { it.copy(rotate = v) } } }
            item { Toggle(stringResource(R.string.try_inverted), s.inverted) { v -> update { it.copy(inverted = v) } } }
            item { Choice(stringResource(R.string.binarizer), s.binarizer, listOf("LOCAL_AVERAGE" to stringResource(R.string.bin_local), "GLOBAL_HISTOGRAM" to stringResource(R.string.bin_global), "FIXED_THRESHOLD" to stringResource(R.string.bin_fixed), "BOOL_CAST" to stringResource(R.string.bin_bool))) { v -> update { it.copy(binarizer = v) } } }
        }
        item { Choice(stringResource(R.string.resolution), s.resolution, listOf("HD" to stringResource(R.string.resolution_hd), "FHD" to stringResource(R.string.resolution_fhd), "QHD" to stringResource(R.string.resolution_qhd))) { v -> update { it.copy(resolution = v) } } }
        item { Choice(stringResource(R.string.scan_frequency), s.speed, listOf("NORMAL" to stringResource(R.string.speed_normal), "FAST" to stringResource(R.string.speed_fast), "FASTEST" to stringResource(R.string.speed_fastest))) { v -> update { it.copy(speed = v) } } }
        item { Toggle(stringResource(R.string.torch_enable), s.torch) { v -> update { it.copy(torch = v) } } }
        item { Toggle(stringResource(R.string.tap_focus), s.tapFocus) { v -> update { it.copy(tapFocus = v) } } }
        item { Choice(stringResource(R.string.appearance), s.theme, listOf("SYSTEM" to stringResource(R.string.theme_system), "LIGHT" to stringResource(R.string.theme_light), "DARK" to stringResource(R.string.theme_dark))) { v -> update { it.copy(theme = v) } } }
        item { Text(stringResource(R.string.camera_settings_note), style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(); TextButton(onClick = { showAbout = true }) { Text(stringResource(R.string.about_app)) } }
    }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false },
        title = { Text(stringResource(R.string.app_name)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            if (support.owned) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.supporter_badge), Modifier.padding(10.dp))
            }
            Text(stringResource(R.string.about_details))
        } }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun SupporterCard(state: SupporterState, golden: Boolean, onPurchase: () -> Unit,
    onRestore: () -> Unit, onGolden: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.free_no_ads), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.free_features))
            Text(stringResource(R.string.supporter_pack), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.supporter_description))
            if (state.owned) {
                Text(stringResource(R.string.supporter_active), color = MaterialTheme.colorScheme.primary)
                Toggle(stringResource(R.string.gold_theme), golden, onGolden)
            } else {
                Button(onClick = onPurchase,
                    enabled = state.available && !state.loading && !state.purchasing && !state.pending,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(when {
                        state.pending -> stringResource(R.string.purchase_pending)
                        state.purchasing -> stringResource(R.string.purchase_processing)
                        state.loading -> stringResource(R.string.store_connecting)
                        state.price != null -> stringResource(R.string.support_price, state.price.orEmpty())
                        else -> stringResource(R.string.purchase_unavailable)
                    })
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it.localized(), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onRestore, enabled = !state.loading && !state.purchasing) {
                Text(stringResource(R.string.restore_retry))
            }
            Text(stringResource(R.string.billing_connection_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
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
