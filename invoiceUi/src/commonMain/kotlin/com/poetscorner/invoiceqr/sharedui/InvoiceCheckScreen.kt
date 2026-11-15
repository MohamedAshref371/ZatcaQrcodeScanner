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
import kotlin.time.Instant

private enum class InvoiceTab { SCANNER, RESULT, HISTORY, STATISTICS, SETTINGS }

private const val invalidInvoiceMessage = "رمز QR غير صالح للفاتورة"

enum class InvoiceDetectionResult { ACCEPTED, INVALID, SAVE_FAILED, INACTIVE }

interface InvoiceDetection {
    fun accept(raw: String): InvoiceDetectionResult
    fun reportPhotoIssue(foundQr: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCheckScreen(
    history: InvoiceHistoryStore,
    camera: (@Composable (InvoiceDetection) -> Unit)? = null,
    selectPhoto: ((InvoiceDetection) -> Unit)? = null,
    shareInvoice: ((String) -> Boolean)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val startTab = if (camera == null) InvoiceTab.RESULT else InvoiceTab.SCANNER
    var tab by remember { mutableStateOf(startTab) }
    var result by remember { mutableStateOf<ParsedInvoice?>(null) }
    var checkedAt by remember { mutableStateOf(0L) }
    var historyFilter by remember { mutableStateOf(InvoiceHistoryFilter()) }

    fun showMessage(message: String) {
        if (snackbar.currentSnackbarData?.visuals?.message == message) return
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun showResult(parsed: ParsedInvoice, saved: InvoiceHistoryEntry) {
        snackbar.currentSnackbarData?.dismiss()
        checkedAt = saved.checkedAt
        result = parsed
        tab = InvoiceTab.RESULT
    }

    fun inspect(raw: String): InvoiceDetectionResult {
        val parsed = ZatcaInvoiceParser.parse(raw)
        if (parsed.error != null) return InvoiceDetectionResult.INVALID

        val normalized = raw.filterNot { it.isWhitespace() }.trimEnd('=')
        val saved = history.save(raw, normalized, parsed.value(1), parsed.value(2), parsed.value(4),
            invoiceHistoryDay(parsed), Clock.System.now().toEpochMilliseconds())
        if (saved == null) {
            showMessage("تعذّر حفظ الفاتورة. حاول مرة أخرى.")
            return InvoiceDetectionResult.SAVE_FAILED
        }

        val invoice = if (saved.raw == raw) parsed else ZatcaInvoiceParser.parse(saved.raw)
        showResult(invoice, saved)
        if (saved.alreadySaved) showMessage("الفاتورة محفوظة بالفعل")
        return InvoiceDetectionResult.ACCEPTED
    }

    fun openSavedInvoice(id: Long) {
        val saved = history.entry(id)
        val invoice = saved?.let { ZatcaInvoiceParser.parse(it.raw) }
        if (saved == null || invoice == null || invoice.error != null) {
            showMessage("تعذّر فتح الفاتورة المحفوظة")
            return
        }
        showResult(invoice, saved)
    }

    val detection = remember(history) {
        object : InvoiceDetection {
            override fun accept(raw: String): InvoiceDetectionResult =
                if (tab == InvoiceTab.SCANNER) inspect(raw) else InvoiceDetectionResult.INACTIVE

            override fun reportPhotoIssue(foundQr: Boolean) {
                if (tab == InvoiceTab.SCANNER) {
                    showMessage(if (foundQr) invalidInvoiceMessage else "لم أتمكن من قراءة رمز QR من الصورة")
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
                    InvoiceResultNavigation(tab, camera != null) { tab = it }
                },
                snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("invoice-error")) },
            ) { padding ->
                Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    val invoice = result
                    if (tab == InvoiceTab.HISTORY) {
                        InvoiceHistoryScreen(history, historyFilter, { historyFilter = it }, ::openSavedInvoice)
                    } else if (tab == InvoiceTab.SCANNER && camera != null) {
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
                            if (inspect(raw) == InvoiceDetectionResult.INVALID) showMessage(invalidInvoiceMessage)
                        }
                    } else {
                        val share: (() -> Unit)? = shareInvoice?.let { action ->
                            {
                                if (!action(invoiceShareSummary(invoice))) showMessage("تعذّرت مشاركة الفاتورة")
                            }
                        }
                        InvoiceResult(invoice, formatInvoiceCheckTime(checkedAt), share) {
                            result = null
                            tab = startTab
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
private fun InvoiceResultNavigation(tab: InvoiceTab, hasScanner: Boolean, select: (InvoiceTab) -> Unit) {
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
                    val itemTab = InvoiceTab.entries[index]
                    NavigationBarItem(
                        selected = itemTab == tab,
                        onClick = { select(itemTab) },
                        enabled = when (itemTab) {
                            InvoiceTab.SCANNER -> hasScanner
                            InvoiceTab.RESULT, InvoiceTab.HISTORY -> true
                            else -> false
                        },
                        modifier = when (itemTab) {
                            InvoiceTab.SCANNER -> Modifier.testTag("invoice-scanner-tab")
                            InvoiceTab.RESULT -> Modifier.testTag("invoice-result-tab")
                            InvoiceTab.HISTORY -> Modifier.testTag("invoice-history-tab")
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
private fun InvoiceResult(invoice: ParsedInvoice, checkedAt: String, onShare: (() -> Unit)?, onAgain: () -> Unit) {
    val phase = if (invoice.phase2) "المرحلة الثانية" else "المرحلة الأولى"
    val type = if (invoice.isSimplified) "فاتورة ضريبية مبسطة" else "فاتورة ضريبية"
    val colors = MaterialTheme.colorScheme
    var advanced by remember(invoice.raw) { mutableStateOf(false) }
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
                    invoiceResultStatus(invoice),
                    Modifier.weight(1f).testTag("invoice-status"),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (onShare != null) {
                    IconButton(onClick = onShare, modifier = Modifier.testTag("invoice-share")) {
                        Icon(ShareIcon, "مشاركة ملخص الفاتورة")
                    }
                }
            }
            Text("$type · $phase")
            invoice.relevantChecks().filterNot { it.passed }.forEach { check ->
                Text("✕ ${invoiceCheckLabel(check)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("تاريخ الفحص: $checkedAt", Modifier.testTag("invoice-checked-at"), style = MaterialTheme.typography.bodySmall)
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
        for (tag in 1..if (invoice.phase2) 6 else 5) {
            if (tag == 3) InvoiceDateCard(invoice.value(tag))
            else InvoiceValueCard(tag, invoiceFieldName(tag), invoice.value(tag))
        }
        if (invoice.phase2) {
            OutlinedButton(onClick = { advanced = !advanced },
                modifier = Modifier.fillMaxWidth().testTag("invoice-advanced")) {
                Text(if (advanced) "إخفاء الحقول المتقدمة" else "عرض الحقول المتقدمة")
            }
            if (advanced) {
                for (tag in 7..9) {
                    val value = if (tag == 9 && !invoice.isSimplified) "اختياري للفاتورة الضريبية" else invoice.value(tag)
                    InvoiceValueCard(tag, invoiceFieldName(tag), value)
                }
            }
        }
        InvoiceVerificationDetails(invoice)
        InvoiceRawQr(invoice.raw)
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

internal fun formatInvoiceCheckTime(checkedAt: Long): String {
    val time = Instant.fromEpochMilliseconds(checkedAt).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.year}/${twoDigits(time.monthNumber)}/${twoDigits(time.dayOfMonth)} ${displayTime(time)}"
}

private fun displayTime(value: LocalDateTime): String {
    val hour = (value.hour + 11) % 12 + 1
    val period = if (value.hour < 12) "ص" else "م"
    return "$hour:${twoDigits(value.minute)}:${twoDigits(value.second)} $period"
}
