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
import com.poetscorner.invoiceqr.shared.InvoiceAnnotationRules
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser
import com.poetscorner.invoiceqr.shared.normalizedInvoicePayload
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private enum class InvoiceTab { SCANNER, RESULT, HISTORY, STATISTICS, SETTINGS }

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
    importFiles: ((InvoiceFilesObserver) -> Unit)? = null,
    cancelFileImport: (() -> Unit)? = null,
    shareInvoice: ((String) -> Boolean)? = null,
    backupActions: InvoiceBackupActions? = null,
    statisticsSource: InvoiceStatisticsSource? = null,
    csvActions: InvoiceCsvActions? = null,
    importSharedFiles: ((InvoiceFilesObserver) -> Boolean)? = null,
    sharedImportRequests: StateFlow<Int>? = null,
    languageSettings: InvoiceLanguageSettings? = null,
) {
    var languagePreference by remember(languageSettings) {
        mutableStateOf(languageSettings?.languagePreference() ?: InvoiceLanguagePreference.DEVICE)
    }
    val language = languagePreference.resolve(languageSettings?.deviceLanguage() ?: InvoiceLanguage.ARABIC)
    val currentLanguage by rememberUpdatedState(language)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val startTab = if (camera == null) InvoiceTab.RESULT else InvoiceTab.SCANNER
    var tab by remember { mutableStateOf(startTab) }
    var result by remember { mutableStateOf<ParsedInvoice?>(null) }
    var savedEntry by remember { mutableStateOf<InvoiceHistoryEntry?>(null) }
    var historyFilter by remember { mutableStateOf(InvoiceHistoryFilter()) }
    var statisticsSettings by remember { mutableStateOf(InvoiceStatisticsSettings()) }
    var backupBusy by remember { mutableStateOf(false) }
    var csvBusy by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    var importSummary by remember { mutableStateOf<InvoiceFilesResult?>(null) }

    fun showMessage(message: String) {
        if (snackbar.currentSnackbarData?.visuals?.message == message) return
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun transferBackup(transfer: (InvoiceBackupObserver) -> Unit) {
        if (backupBusy || csvBusy || importBusy) return
        backupBusy = true
        transfer { result ->
            backupBusy = false
            result.message(currentLanguage)?.let(::showMessage)
        }
    }

    fun transferCsv(transfer: (InvoiceCsvObserver) -> Unit) {
        if (csvBusy || backupBusy || importBusy) return
        csvBusy = true
        transfer { status ->
            csvBusy = false
            when (status) {
                InvoiceCsvStatus.EXPORTED -> showMessage(language.text("تم تصدير CSV", "CSV exported"))
                InvoiceCsvStatus.FAILED -> showMessage(language.text("تعذّر تصدير ملف CSV", "Could not export the CSV file"))
                InvoiceCsvStatus.CANCELLED -> Unit
            }
        }
    }

    fun startFileImport(start: (InvoiceFilesObserver) -> Boolean) {
        if (importBusy || backupBusy || csvBusy) return
        importProgress = ""
        var completed = false
        val started = start(object : InvoiceFilesObserver {
            override fun progress(file: Int, total: Int, page: Int, pages: Int) {
                importProgress = if (pages > 0) language.text("ملف $file من $total، صفحة $page من $pages", "File $file of $total, page $page of $pages") else language.text("ملف $file من $total", "File $file of $total")
            }

            override fun complete(result: InvoiceFilesResult) {
                completed = true
                importBusy = false
                importProgress = ""
                if (result.totalFiles == 0 && result.cancelled) return
                importSummary = result
                showMessage(if (result.cancelled) language.text("توقف الاستيراد", "Import stopped") else language.text("اكتمل الاستيراد", "Import complete"))
            }
        })
        if (started && !completed) {
            importBusy = true
            importSummary = null
            tab = InvoiceTab.SCANNER
        }
    }

    val sharedImportRevision = sharedImportRequests?.collectAsState()?.value ?: 0
    var handledSharedImport by remember { mutableIntStateOf(0) }
    LaunchedEffect(sharedImportRevision, importBusy, backupBusy, csvBusy) {
        if (sharedImportRevision != handledSharedImport && !importBusy && !backupBusy && !csvBusy) {
            handledSharedImport = sharedImportRevision
            importSharedFiles?.let(::startFileImport)
        }
    }

    fun showResult(parsed: ParsedInvoice, saved: InvoiceHistoryEntry) {
        snackbar.currentSnackbarData?.dismiss()
        savedEntry = saved
        result = parsed
        tab = InvoiceTab.RESULT
    }

    fun inspect(raw: String): InvoiceDetectionResult {
        val parsed = ZatcaInvoiceParser.parse(raw)
        if (parsed.error != null) return InvoiceDetectionResult.INVALID

        val normalized = normalizedInvoicePayload(raw)
        val saved = history.save(raw, normalized, parsed.value(1), parsed.value(2), parsed.value(4), parsed.value(5),
            invoiceHistoryDay(parsed), Clock.System.now().toEpochMilliseconds())
        if (saved == null) {
            showMessage(currentLanguage.text("تعذّر حفظ الفاتورة. حاول مرة أخرى.", "Could not save the invoice. Try again."))
            return InvoiceDetectionResult.SAVE_FAILED
        }

        val invoice = if (saved.raw == raw) parsed else ZatcaInvoiceParser.parse(saved.raw)
        showResult(invoice, saved)
        if (saved.alreadySaved) showMessage(currentLanguage.text("الفاتورة محفوظة بالفعل", "This invoice is already saved"))
        return InvoiceDetectionResult.ACCEPTED
    }

    fun openSavedInvoice(id: Long) {
        val saved = history.entry(id)
        val invoice = saved?.let { ZatcaInvoiceParser.parse(it.raw) }
        if (saved == null || invoice == null || invoice.error != null) {
            showMessage(language.text("تعذّر فتح الفاتورة المحفوظة", "Could not open the saved invoice"))
            return
        }
        showResult(invoice, saved)
    }

    val detection = remember(history) {
        object : InvoiceDetection {
            override fun accept(raw: String): InvoiceDetectionResult =
                if (tab == InvoiceTab.SCANNER && !importBusy) inspect(raw) else InvoiceDetectionResult.INACTIVE

            override fun reportPhotoIssue(foundQr: Boolean) {
                if (tab == InvoiceTab.SCANNER) {
                    showMessage(if (foundQr) currentLanguage.text("رمز QR غير صالح للفاتورة", "Invalid invoice QR code") else currentLanguage.text("لم أتمكن من قراءة رمز QR من الصورة", "Could not read a QR code from the image"))
                }
            }
        }
    }

    InvoiceAppTheme(standardInvoiceColors(isSystemInDarkTheme())) {
        CompositionLocalProvider(
            LocalInvoiceLanguage provides language,
            LocalLayoutDirection provides if (language == InvoiceLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            InvoiceIconTile(ScanIcon, Modifier.padding(start = 16.dp, end = 8.dp))
                        },
                        title = {
                            Column {
                                Text(language.text("قارئ فاتورة", "Invoice QR Reader"), style = MaterialTheme.typography.titleMedium)
                                Text(language.text("قراءة دقيقة، وتفاصيل واضحة", "Scan and review invoices"), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = {
                    InvoiceResultNavigation(tab, camera != null, backupActions != null, statisticsSource != null,
                        backupBusy || importBusy) {
                        tab = it
                    }
                },
                snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("invoice-error")) },
            ) { padding ->
                Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    val invoice = result
                    val entry = savedEntry
                    if (tab == InvoiceTab.SETTINGS && backupActions != null) {
                        InvoiceBackupSettings(backupBusy || csvBusy || importBusy,
                            languagePreference = languagePreference,
                            onLanguageChange = languageSettings?.let { settings ->
                                { preference ->
                                    settings.saveLanguage(preference)
                                    languagePreference = preference
                                    snackbar.currentSnackbarData?.dismiss()
                                }
                            },
                            onExport = { transferBackup(backupActions::exportBackup) },
                            onImport = { transferBackup(backupActions::importBackup) })
                    } else if (tab == InvoiceTab.STATISTICS && statisticsSource != null) {
                        InvoiceStatisticsScreen(statisticsSource, statisticsSettings,
                            onChange = { statisticsSettings = it },
                            exportEnabled = csvActions != null && !csvBusy && !backupBusy && !importBusy,
                            onExport = { snapshot, label, net ->
                                csvActions?.let { actions -> transferCsv { actions.exportStatisticsCsv(snapshot, label, net, language, it) } }
                            })
                    } else if (tab == InvoiceTab.HISTORY) {
                        InvoiceHistoryScreen(history, historyFilter, { historyFilter = it }, ::openSavedInvoice,
                            exportEnabled = csvActions != null && !csvBusy && !backupBusy && !importBusy,
                            onExport = {
                                csvActions?.let { actions -> transferCsv { actions.exportHistoryCsv(historyFilter.toQuery(), language, it) } }
                            })
                    } else if (tab == InvoiceTab.SCANNER && camera != null) {
                        InvoiceScannerPanel(
                            camera = { camera(detection) },
                            selectPhoto = selectPhoto?.let { action -> { if (!importBusy) action(detection) } },
                            selectPhotoEnabled = !importBusy,
                            importFiles = importFiles?.let { open ->
                                { startFileImport { observer -> open(observer); true } }
                            },
                            importBusy = importBusy,
                            importProgress = importProgress,
                            importSummary = importSummary,
                            cancelImport = cancelFileImport,
                        )
                    } else if (invoice == null || entry == null) {
                        InvoiceManualEntry(
                            busy = false,
                            title = language.text("لم تُقرأ فاتورة بعد", "No invoice scanned yet"),
                            hint = language.text("هل لديك نص Base64 لرمز QR الخاص بالفاتورة من برنامج آخر؟ الصقه في الصندوق أدناه واضغط «فحص» لعرض نتيجة الفاتورة.", "Paste the Base64 text of an invoice QR code below and tap Check to view the invoice."),
                            fieldLabel = language.text("نص Base64 للفاتورة", "Invoice Base64 text"),
                            buttonLabel = language.text("فحص", "Check"),
                            icon = { InvoiceIconTile(ReceiptIcon) },
                        ) { raw ->
                            if (inspect(raw) == InvoiceDetectionResult.INVALID) showMessage(language.text("رمز QR غير صالح للفاتورة", "Invalid invoice QR code"))
                        }
                    } else {
                        val share: (() -> Unit)? = shareInvoice?.let { action ->
                            {
                                if (!action(invoiceShareSummary(invoice, language))) showMessage(language.text("تعذّرت مشاركة الفاتورة", "Could not share the invoice"))
                            }
                        }
                        InvoiceResult(invoice, entry, share, onSaveAnnotation = { fields ->
                            val saved = InvoiceAnnotationRules.isValid(fields.number, fields.category, fields.note) &&
                                history.updateAnnotation(entry.id, fields)
                            if (saved) savedEntry = entry.copy(annotation = fields)
                            saved
                        }) {
                            result = null
                            savedEntry = null
                            tab = startTab
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceScannerPanel(camera: @Composable () -> Unit, selectPhoto: (() -> Unit)?, selectPhotoEnabled: Boolean,
                                importFiles: (() -> Unit)?, importBusy: Boolean, importProgress: String,
                                importSummary: InvoiceFilesResult?, cancelImport: (() -> Unit)?) {
    val language = LocalInvoiceLanguage.current
    Column(Modifier.fillMaxSize().testTag("invoice-scanner")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(language.text("مسح رمز QR", "Scan a QR code"), style = MaterialTheme.typography.titleLarge)
                Text(language.text("وجّه الكاميرا إلى رمز QR في الفاتورة.", "Point the camera at the invoice QR code."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selectPhoto != null) {
                FilledTonalButton(onClick = selectPhoto, enabled = selectPhotoEnabled,
                    modifier = Modifier.testTag("invoice-scan-photo")) {
                    Text(language.text("اختيار صورة", "Choose photo"))
                }
            }
        }
        if (importFiles != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = importFiles, enabled = !importBusy,
                    modifier = Modifier.testTag("invoice-import-files")) { Text(language.text("استيراد صور أو PDF", "Import images or PDFs")) }
                if (importBusy && cancelImport != null) {
                    TextButton(onClick = cancelImport, modifier = Modifier.testTag("invoice-import-cancel")) { Text(language.text("إلغاء", "Cancel")) }
                }
            }
            if (importProgress.isNotEmpty()) Text(importProgress, Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall)
            if (importSummary != null) {
                Text(language.text("${importSummary.processedFiles} من ${importSummary.totalFiles} ملفات: ", "${importSummary.processedFiles} of ${importSummary.totalFiles} files: ") +
                    language.text("أضيفت ${importSummary.added}، مكررة ${importSummary.duplicates}، ", "added ${importSummary.added}, duplicates ${importSummary.duplicates}, ") +
                    language.text("تحتاج مراجعة ${importSummary.needsReview}، QR غير صالح ${importSummary.invalidQr}، ", "need review ${importSummary.needsReview}, invalid QR ${importSummary.invalidQr}, ") +
                    language.text("غير مقروءة ${importSummary.unreadable}، فشل الحفظ ${importSummary.saveFailed}", "unreadable ${importSummary.unreadable}, save failures ${importSummary.saveFailed}") +
                    if (importSummary.cancelled) language.text(" (توقف)", " (stopped)") else "",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { camera() }
    }
}

@Composable
private fun InvoiceResultNavigation(tab: InvoiceTab, hasScanner: Boolean, hasSettings: Boolean, hasStatistics: Boolean, busy: Boolean,
    select: (InvoiceTab) -> Unit) {
    val language = LocalInvoiceLanguage.current
    val items = listOf(
        language.text("الماسح", "Scanner") to ScanIcon, language.text("النتيجة", "Result") to ReceiptIcon, language.text("السجل", "History") to HistoryIcon,
        language.text("الإحصاءات", "Statistics") to StatisticsIcon, language.text("الإعدادات", "Settings") to SettingsIcon,
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
                        enabled = !busy && when (itemTab) {
                            InvoiceTab.SCANNER -> hasScanner
                            InvoiceTab.RESULT, InvoiceTab.HISTORY -> true
                            InvoiceTab.SETTINGS -> hasSettings
                            InvoiceTab.STATISTICS -> hasStatistics
                        },
                        modifier = when (itemTab) {
                            InvoiceTab.SCANNER -> Modifier.testTag("invoice-scanner-tab")
                            InvoiceTab.RESULT -> Modifier.testTag("invoice-result-tab")
                            InvoiceTab.HISTORY -> Modifier.testTag("invoice-history-tab")
                            InvoiceTab.STATISTICS -> Modifier.testTag("invoice-statistics-tab")
                            InvoiceTab.SETTINGS -> Modifier.testTag("invoice-settings-tab")
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
private fun InvoiceResult(invoice: ParsedInvoice, entry: InvoiceHistoryEntry, onShare: (() -> Unit)?,
    onSaveAnnotation: (InvoiceAnnotationFields) -> Boolean, onAgain: () -> Unit) {
    val language = LocalInvoiceLanguage.current
    val phase = if (invoice.phase2) language.text("المرحلة الثانية", "Phase two") else language.text("المرحلة الأولى", "Phase one")
    val type = if (invoice.isSimplified) language.text("فاتورة ضريبية مبسطة", "Simplified tax invoice") else language.text("فاتورة ضريبية", "Tax invoice")
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
                    invoiceResultStatus(invoice, language),
                    Modifier.weight(1f).testTag("invoice-status"),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (onShare != null) {
                    IconButton(onClick = onShare, modifier = Modifier.testTag("invoice-share")) {
                        Icon(ShareIcon, language.text("مشاركة ملخص الفاتورة", "Share invoice summary"))
                    }
                }
            }
            Text("$type · $phase")
            invoice.relevantChecks().filterNot { it.passed }.forEach { check ->
                Text("✕ ${invoiceCheckLabel(check, language)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(language.text("تاريخ الفحص: ${formatInvoiceCheckTime(entry.checkedAt, language)}", "Checked: ${formatInvoiceCheckTime(entry.checkedAt, language)}"),
                Modifier.testTag("invoice-checked-at"), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            language.text("يفحص التطبيق بنية QR وحقول الفاتورة محليًا، ولا يتحقق من صحة التوقيع أو اعتماد الفاتورة رسميًا.", "The app checks QR structure and invoice fields locally. Signature validity and official acceptance require separate verification."),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            language.text("تفاصيل الفاتورة", "Invoice details"),
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).testTag("invoice-result"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        for (tag in 1..if (invoice.phase2) 6 else 5) {
            if (tag == 3) InvoiceDateCard(invoice.value(tag))
            else InvoiceValueCard(tag, invoiceFieldName(tag, language), invoice.value(tag))
        }
        IosInvoiceAnnotations(entry, onSaveAnnotation)
        if (invoice.phase2) {
            OutlinedButton(onClick = { advanced = !advanced },
                modifier = Modifier.fillMaxWidth().testTag("invoice-advanced")) {
                Text(if (advanced) language.text("إخفاء الحقول المتقدمة", "Hide advanced fields") else language.text("عرض الحقول المتقدمة", "Show advanced fields"))
            }
            if (advanced) {
                for (tag in 7..9) {
                    val value = if (tag == 9 && !invoice.isSimplified) language.text("اختياري للفاتورة الضريبية", "Optional for a tax invoice") else invoice.value(tag)
                    InvoiceValueCard(tag, invoiceFieldName(tag, language), value)
                }
            }
        }
        InvoiceVerificationDetails(invoice)
        InvoiceRawQr(invoice.raw)
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text(language.text("فحص فاتورة أخرى", "Check another invoice")) }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InvoiceValueCard(tag: Int, title: String, value: String) {
    val language = LocalInvoiceLanguage.current
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
                            value.ifBlank { language.text("غير موجود", "Missing") },
                            Modifier.testTag("invoice-field-$tag"),
                            style = if (tag >= 2 && value.isNotBlank()) style.copy(textDirection = TextDirection.Ltr) else style,
                            fontWeight = if (tag == 1 || tag == 4) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    if (tag == 2) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }, enabled = value.isNotBlank()) {
                            Icon(CopyIcon, language.text("نسخ الرقم الضريبي", "Copy VAT number"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceDateCard(value: String) {
    val language = LocalInvoiceLanguage.current
    val time = remember(value) { runCatching { ZatcaInvoiceParser.parseTimestamp(value) }.getOrNull() }
    val title = when {
        time == null -> language.text("التاريخ والوقت", "Date and time")
        value.endsWith('Z') -> language.text("التاريخ والوقت (توقيت عالمي)", "Date and time (UTC)")
        else -> language.text("التاريخ والوقت (توقيت محلي)", "Date and time (local time)")
    }
    InvoiceFieldCard {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InvoiceFieldLabel(title)
                val date = if (time == null) value.ifBlank { language.text("غير موجود", "Missing") }
                    else if (language == InvoiceLanguage.ARABIC) {
                        "${weekdays[time.date.dayOfWeek.ordinal]} ${time.dayOfMonth} ${months[time.monthNumber - 1]} ${time.year}"
                    } else time.date.toString()
                Text(language.text("التاريخ: $date", "Date: $date"), Modifier.testTag("invoice-field-3"), style = MaterialTheme.typography.bodyLarge)
                if (time != null) Text(language.text("الوقت: ${displayTime(time, language)}", "Time: ${displayTime(time, language)}"), style = MaterialTheme.typography.bodyLarge)
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

internal fun formatInvoiceCheckTime(checkedAt: Long, language: InvoiceLanguage): String {
    val time = Instant.fromEpochMilliseconds(checkedAt).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.year}/${twoDigits(time.monthNumber)}/${twoDigits(time.dayOfMonth)} ${displayTime(time, language)}"
}

private fun displayTime(value: LocalDateTime, language: InvoiceLanguage): String {
    val hour = (value.hour + 11) % 12 + 1
    val period = if (value.hour < 12) language.text("ص", "AM") else language.text("م", "PM")
    return "$hour:${twoDigits(value.minute)}:${twoDigits(value.second)} $period"
}
