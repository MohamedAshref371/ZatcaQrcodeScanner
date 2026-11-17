@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.Invoice
import com.poetscorner.invoiceqr.scanner.IosInvoiceFileReader
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.*

/** Only native file access/rendering. Shared Kotlin owns every database operation. */
internal class IosScannerFiles : ScannerFiles {
    lateinit var historyLabels: suspend () -> HistoryExportText
    lateinit var statisticsLabels: suspend (StatisticsRequest, StatisticsResult) -> StatisticsExportText
    lateinit var saveDocument: suspend (NSURL) -> Boolean

    override fun describe(uri: String): Triple<String, InvoiceFileType?, String> {
        val url = requireNotNull(NSURL.URLWithString(uri))
        val name = url.lastPathComponent.orEmpty().take(120)
        val type = when(url.pathExtension?.lowercase()) {
            "pdf" -> InvoiceFileType.PDF
            "png", "jpg", "jpeg", "webp", "heic", "heif", "bmp", "gif" -> InvoiceFileType.IMAGE
            else -> null
        }
        return Triple(uri, type, name)
    }
    override suspend fun readImage(uri: String, settings: ScannerSettings): Invoice? =
        IosInvoiceFileReader.image(requireNotNull(NSURL.URLWithString(uri)), settings).singleOrNull()
    override suspend fun readPdf(uri: String, settings: ScannerSettings, onPage: (Int, Int) -> Unit): List<Invoice> =
        IosInvoiceFileReader.pdf(requireNotNull(NSURL.URLWithString(uri)), settings, onPage)
    override suspend fun historyText() = historyLabels()
    override suspend fun statisticsText(request: StatisticsRequest, result: StatisticsResult) = statisticsLabels(request, result)

    override suspend fun outputDocument(uri: String, backup: Boolean, saving: () -> Unit, write: suspend (Sink) -> Unit) {
        val directory = Path(NSTemporaryDirectory(), "invoiceqr-export-${NSUUID().UUIDString}")
        SystemFileSystem.createDirectories(directory)
        val path = Path(directory, uri.substringAfterLast('/'))
        try {
            SystemFileSystem.sink(path).buffered().use { write(it) }
            if (backup && requireNotNull(SystemFileSystem.metadataOrNull(path)?.size) > HistoryBackupFormat.MAX_BYTES)
                throw BackupTooLargeException()
            saving()
            if (!saveDocument(NSURL.fileURLWithPath(path.toString()))) throw DocumentExportCancelled()
        } finally {
            SystemFileSystem.delete(path, mustExist = false)
            SystemFileSystem.delete(directory, mustExist = false)
        }
    }
    override suspend fun <T> withBackupInput(uri: String, read: suspend (BackupJsonInput) -> T): T {
        val url = requireNotNull(NSURL.URLWithString(uri))
        val path = Path(requireNotNull(url.path))
        val size = requireNotNull(SystemFileSystem.metadataOrNull(path)?.size)
        if (size > HistoryBackupFormat.MAX_BYTES) throw BackupTooLargeException()
        return SystemFileSystem.source(path).buffered().use { read(StreamingBackupJsonInput(it)) }
    }
}
