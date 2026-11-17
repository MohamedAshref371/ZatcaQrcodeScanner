package com.poetscorner.invoiceqr.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.ContextCompat
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.invoiceFileDisplayName
import com.poetscorner.invoiceqr.invoiceFileType
import com.poetscorner.invoiceqr.InvoiceFileType as AndroidFileType
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.*
import com.poetscorner.invoiceqr.scanner.*
import kotlinx.io.Sink
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.File

internal class AndroidScannerFiles(private val app: Application) : ScannerFiles {
    override fun describe(uri: String): Triple<String, InvoiceFileType?, String> {
        val url = Uri.parse(uri)
        val type = when(app.invoiceFileType(url)) {
            AndroidFileType.IMAGE -> InvoiceFileType.IMAGE
            AndroidFileType.PDF -> InvoiceFileType.PDF
            null -> null
        }
        return Triple(uri, type, app.invoiceFileDisplayName(url))
    }
    override suspend fun readImage(uri: String, settings: ScannerSettings): Invoice? {
        val bitmap = decodeGallery(app, Uri.parse(uri))
        return try { ZxingDecoder().use { it.readImportedImage(bitmap, settings) }?.let(ZatcaParser::parse) }
        finally { bitmap.recycle() }
    }
    override suspend fun readPdf(uri: String, settings: ScannerSettings, onPage: (Int, Int) -> Unit): List<Invoice> =
        ZxingDecoder().use { decoder -> invoicesInPdf(app, Uri.parse(uri), onPage) { decoder.readAll(it, settings) } }

    override suspend fun historyText(): HistoryExportText {
        val names = mapOf(1 to R.string.tag_seller, 2 to R.string.tag_vat,
            3 to R.string.export_invoice_date, 4 to R.string.tag_total,
            5 to R.string.tag_tax, 6 to R.string.tag_hash, 7 to R.string.invoice_number,
            8 to R.string.invoice_category, 9 to R.string.invoice_note)
            .mapValues { ContextCompat.getString(app, it.value) }
        val context = ContextCompat.getContextForLanguage(app)
        return HistoryExportText(names) { context.getString(InvoiceCategories.label(it)) }
    }
    override suspend fun statisticsText(request: StatisticsRequest, result: StatisticsResult): StatisticsExportText {
        val context = ContextCompat.getContextForLanguage(app)
        val net = request.categoryCalculationActive
        val headers = listOf(request.grouping.androidLabel(), R.string.stats_count_label,
            if (net) {
                if (result.summary.missingTotal > 0) R.string.stats_available_net_total else R.string.stats_net_total
            } else if (result.summary.missingTotal > 0) R.string.stats_available_total else R.string.stats_total_including_tax,
            if (net) {
                if (result.summary.missingTax > 0) R.string.stats_available_net_tax else R.string.stats_net_tax
            } else if (result.summary.missingTax > 0) R.string.stats_available_tax else R.string.tag_tax,
            R.string.stats_missing_total_count, R.string.stats_missing_tax_count).map(context::getString)
        return StatisticsExportText(headers, context.getString(R.string.stats_summary)) {
            statisticsGroupTitle(context, request, it)
        }
    }
    override suspend fun outputDocument(uri: String, backup: Boolean, saving: () -> Unit, write: suspend (Sink) -> Unit) {
        val cache = File.createTempFile("history-export-", ".json", app.cacheDir)
        try {
            cache.outputStream().asSink().buffered().use { write(it) }
            if (backup && cache.length() > HistoryBackupFormat.MAX_BYTES) throw BackupTooLargeException()
            saving()
            requireNotNull(app.contentResolver.openOutputStream(Uri.parse(uri), "wt")).use { output ->
                cache.inputStream().use { input ->
                    HistoryBackup.copyLimited(input, output, if (backup) HistoryBackupFormat.MAX_BYTES else Long.MAX_VALUE)
                }
            }
        } finally {
            cache.delete()
        }
    }

    override suspend fun <T> withBackupInput(uri: String, read: suspend (BackupJsonInput) -> T): T {
        val cache = File.createTempFile("history-import-", ".json", app.cacheDir)
        try {
            requireNotNull(app.contentResolver.openInputStream(Uri.parse(uri))).use { input ->
                cache.outputStream().use { HistoryBackup.copyLimited(input, it) }
            }
            return cache.inputStream().asSource().buffered().use { read(StreamingBackupJsonInput(it)) }
        } finally { cache.delete() }
    }
}
