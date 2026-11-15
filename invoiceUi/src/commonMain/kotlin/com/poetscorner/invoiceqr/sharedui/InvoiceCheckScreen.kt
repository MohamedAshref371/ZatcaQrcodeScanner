package com.poetscorner.invoiceqr.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.poetscorner.invoiceqr.shared.ParsedInvoice
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

interface InvoiceDetection {
    fun accept(raw: String): Boolean
    fun reportPhotoIssue(foundQr: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCheckScreen(
    camera: (@Composable (InvoiceDetection) -> Unit)? = null,
    selectPhoto: ((InvoiceDetection) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var scannerSelected by remember { mutableStateOf(camera != null) }
    var result by remember { mutableStateOf<ParsedInvoice?>(null) }
    var checkedAt by remember { mutableStateOf("") }

    fun showMessage(message: String) {
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun inspect(raw: String): Boolean {
        val parsed = ZatcaInvoiceParser.parse(raw)
        if (parsed.error != null) return false
        snackbar.currentSnackbarData?.dismiss()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        checkedAt = "${now.year}/${twoDigits(now.monthNumber)}/${twoDigits(now.dayOfMonth)} ${displayTime(now)}"
        result = parsed
        scannerSelected = false
        return true
    }

    val detection = remember {
        object : InvoiceDetection {
            override fun accept(raw: String): Boolean = scannerSelected && inspect(raw)

            override fun reportPhotoIssue(foundQr: Boolean) {
                if (scannerSelected) {
                    showMessage(if (foundQr) "رمز QR غير صالح للفاتورة" else "لم أتمكن من قراءة رمز QR من الصورة")
                }
            }
        }
    }

    InvoiceAppTheme(standardInvoiceColors(isSystemInDarkTheme())) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            InvoiceIconTile(ScanIcon, Modifier.padding(start = 16.dp, end = 8.dp))
                        },
                        title = {
                            Column {
                                Text("قارئ فاتورة", style = MaterialTheme.typography.titleMedium)
                                Text("قراءة دقيقة، وتفاصيل واضحة", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = {
                    InvoiceResultNavigation(scannerSelected, camera != null) { scannerSelected = it }
                },
                snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("invoice-error")) },
            ) { padding ->
                Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    val invoice = result
                    if (scannerSelected && camera != null) {
                        InvoiceScannerPanel(
                            camera = { camera(detection) },
                            selectPhoto = selectPhoto?.let { action -> { action(detection) } },
                        )
                    } else if (invoice == null) {
                        InvoiceManualEntry(
                            busy = false,
                            title = "لم تُقرأ فاتورة بعد",
                            hint = "هل لديك نص Base64 لرمز QR الخاص بالفاتورة من برنامج آخر؟ الصقه في الصندوق أدناه واضغط «فحص» لعرض نتيجة الفاتورة.",
                            fieldLabel = "نص Base64 للفاتورة",
                            buttonLabel = "فحص",
                            icon = { InvoiceIconTile(ReceiptIcon) },
                        ) { raw ->
                            if (!inspect(raw)) showMessage("رمز QR غير صالح للفاتورة")
                        }
                    } else {
                        InvoiceResult(invoice, checkedAt) {
                            result = null
                            scannerSelected = camera != null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceScannerPanel(camera: @Composable () -> Unit, selectPhoto: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().testTag("invoice-scanner")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مسح رمز QR", style = MaterialTheme.typography.titleLarge)
                Text("وجّه الكاميرا إلى رمز QR في الفاتورة.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selectPhoto != null) {
                FilledTonalButton(onClick = selectPhoto, modifier = Modifier.testTag("invoice-scan-photo")) {
                    Text("اختيار صورة")
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { camera() }
    }
}

@Composable
private fun InvoiceResultNavigation(scannerSelected: Boolean, hasScanner: Boolean, select: (Boolean) -> Unit) {
    val items = listOf(
        "الماسح" to ScanIcon, "النتيجة" to ReceiptIcon, "السجل" to HistoryIcon,
        "الإحصاءات" to StatisticsIcon, "الإعدادات" to SettingsIcon,
    )
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelHeight = with(LocalDensity.current) { labelStyle.lineHeight.toDp() }
    val barHeight = maxOf(64.dp, 46.dp + labelHeight)

    // Keep the home-gesture inset outside the bar's white surface.
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        Surface(
            color = colors.surfaceContainerLowest,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            NavigationBar(
                modifier = Modifier.height(barHeight),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
            ) {
                items.forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(
                        selected = index == if (scannerSelected) 0 else 1,
                        onClick = { select(index == 0) },
                        enabled = index == 1 || (index == 0 && hasScanner),
                        modifier = when (index) {
                            0 -> Modifier.testTag("invoice-scanner-tab")
                            1 -> Modifier.testTag("invoice-result-tab")
                            else -> Modifier
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(title, style = labelStyle) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.onPrimaryContainer,
                            selectedTextColor = colors.primary,
                            indicatorColor = colors.primaryContainer,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
private fun InvoiceIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(modifier.size(44.dp), color = container, shape = MaterialTheme.shapes.small) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint) }
    }
}

@Composable
private fun InvoiceResult(invoice: ParsedInvoice, checkedAt: String, onAgain: () -> Unit) {
    val phase = if (invoice.phase2) "المرحلة الثانية" else "المرحلة الأولى"
    val type = if (invoice.isSimplified) "فاتورة ضريبية مبسطة" else "فاتورة ضريبية"
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().testTag("invoice-result-list").verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InvoiceSummaryCard(invoice.passed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InvoiceIconTile(
                    if (invoice.passed) CheckIcon else InfoIcon,
                    container = colors.surfaceContainerLowest,
                    tint = if (invoice.passed) colors.primary else colors.error,
                )
                Text(
                    if (invoice.passed) "اجتاز فحوص QR المحلية" else "لم يجتز فحوص QR المحلية",
                    Modifier.weight(1f).testTag("invoice-status"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text("$type · $phase")
            Text("تاريخ الفحص: $checkedAt", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "يفحص التطبيق بنية QR وحقول الفاتورة محليًا، ولا يتحقق من صحة التوقيع أو اعتماد الفاتورة رسميًا.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            "تفاصيل الفاتورة",
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).testTag("invoice-result"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        InvoiceValueCard(1, "اسم البائع", invoice.value(1))
        InvoiceValueCard(2, "الرقم الضريبي", invoice.value(2))
        InvoiceDateCard(invoice.value(3))
        InvoiceValueCard(4, "إجمالي الفاتورة", invoice.value(4))
        InvoiceValueCard(5, "الضريبة", invoice.value(5))
        if (invoice.phase2) InvoiceValueCard(6, "بصمة الفاتورة", invoice.value(6))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("فحص فاتورة أخرى") }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InvoiceValueCard(tag: Int, title: String, value: String) {
    val clipboard = LocalClipboardManager.current
    InvoiceFieldCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InvoiceFieldLabel(title)
            val direction = if (tag == 2) LayoutDirection.Ltr else LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer(Modifier.weight(1f)) {
                        val style = if (tag == 4) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge
                        Text(
                            value.ifBlank { "غير موجود" },
                            Modifier.testTag("invoice-field-$tag"),
                            style = if (tag >= 2 && value.isNotBlank()) style.copy(textDirection = TextDirection.Ltr) else style,
                            fontWeight = if (tag == 1 || tag == 4) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    if (tag == 2) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }, enabled = value.isNotBlank()) {
                            Icon(CopyIcon, "نسخ الرقم الضريبي")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceDateCard(value: String) {
    val time = remember(value) { runCatching { ZatcaInvoiceParser.parseTimestamp(value) }.getOrNull() }
    val title = when {
        time == null -> "التاريخ والوقت"
        value.endsWith('Z') -> "التاريخ والوقت (توقيت عالمي)"
        else -> "التاريخ والوقت (توقيت محلي)"
    }
    InvoiceFieldCard {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InvoiceFieldLabel(title)
                val date = if (time == null) value.ifBlank { "غير موجود" }
                    else "${weekdays[time.date.dayOfWeek.ordinal]} ${time.dayOfMonth} ${months[time.monthNumber - 1]} ${time.year}"
                Text("التاريخ: $date", Modifier.testTag("invoice-field-3"), style = MaterialTheme.typography.bodyLarge)
                if (time != null) Text("الوقت: ${displayTime(time)}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun InvoiceFieldLabel(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val weekdays = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
private val months = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

private fun twoDigits(value: Int) = value.toString().padStart(2, '0')

private fun displayTime(value: LocalDateTime): String {
    val hour = (value.hour + 11) % 12 + 1
    val period = if (value.hour < 12) "ص" else "م"
    return "$hour:${twoDigits(value.minute)}:${twoDigits(value.second)} $period"
}
