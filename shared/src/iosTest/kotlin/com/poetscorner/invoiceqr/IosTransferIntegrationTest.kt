@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr

import androidx.room.Room
import com.poetscorner.invoiceqr.data.*
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.ui.*
import com.poetscorner.invoiceqr.resources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*

class IosTransferIntegrationTest {
    @Test fun selectedPhotoAndPdfUseSharedResultsDuplicatesHistoryAndExports() = runTest {
        val directory = NSTemporaryDirectory() + "invoiceqr-integration-${NSUUID().UUIDString}"
        SystemFileSystem.createDirectories(Path(directory))
        val png = "$directory/photo.png"
        val pdf = "$directory/document.pdf"
        val database = Room.inMemoryDatabaseBuilder<ScanDatabase>().buildInvoiceDatabase()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main)
        try {
            val modules = invoiceQrModules.trimIndent().lines()
            val edge = (modules.size + 8) * 5.0
            fun draw() {
                UIColor.whiteColor.setFill(); UIRectFill(CGRectMake(0.0, 0.0, edge, edge))
                UIColor.blackColor.setFill()
                for ((y, row) in modules.withIndex()) for ((x, module) in row.withIndex()) {
                    if (module == '#') UIRectFill(CGRectMake((x + 4) * 5.0, (y + 4) * 5.0, 5.0, 5.0))
                }
            }
            UIGraphicsBeginImageContextWithOptions(platform.CoreGraphics.CGSizeMake(edge, edge), false, 1.0)
            try {
                draw()
                assertTrue(requireNotNull(UIImagePNGRepresentation(requireNotNull(UIGraphicsGetImageFromCurrentImageContext())))
                    .writeToFile(png, atomically = true))
            } finally { UIGraphicsEndImageContext() }
            assertTrue(UIGraphicsBeginPDFContextToFile(pdf, CGRectMake(0.0, 0.0, edge, edge), null))
            try { repeat(2) { UIGraphicsBeginPDFPage(); draw() } } finally { UIGraphicsEndPDFContext() }
            val files = IosScannerFiles()
            val savedFiles = mutableMapOf<String, String>()
            files.saveDocument = { url ->
                savedFiles[requireNotNull(url.lastPathComponent)] = SystemFileSystem.source(Path(requireNotNull(url.path)))
                    .buffered().use { it.readString() }
                true
            }
            files.historyLabels = { HistoryExportText((1..9).associateWith { "field$it" }) { "category$it" } }
            files.statisticsLabels = { _, _ -> StatisticsExportText(List(6) { "column$it" }, "Summary") { "group" } }
            val stores = InvoicePreferenceStores(directory.toPath() / "datastore")
            val filters = StatisticsFilterStore(TestPreferences())
            val controller = ScannerController(database, SettingsRepository(stores.settings), ReviewRepository(stores.review),
                filters, MemoryScannerSavedState(), files, scope)
            withContext(Dispatchers.Main) {
                requireNotNull(controller.importInvoiceFiles(listOf(NSURL.fileURLWithPath(png).absoluteString!!))).join()
                assertEquals(1, controller.screen.value)
                assertEquals(INVOICE_QR_PAYLOAD, controller.result.value?.invoice?.raw)
                val original = requireNotNull(controller.result.value)
                requireNotNull(controller.importInvoiceFiles(listOf(NSURL.fileURLWithPath(png).absoluteString!!))).join()
                assertEquals(Res.string.invoice_already_saved, controller.message.value?.id)
                assertEquals(original.checkedAt, controller.result.value?.checkedAt)
                requireNotNull(controller.importInvoiceFiles(listOf(NSURL.fileURLWithPath(pdf).absoluteString!!,
                    NSURL.fileURLWithPath(png).absoluteString!!))).join()
                assertEquals(2, controller.screen.value)
                assertEquals(ImageImportSummary(duplicates = 1, processedFiles = 2, totalFiles = 2), controller.imageImportSummary.value)
                assertEquals(1, database.scans().count())
                requireNotNull(controller.export("history.csv", true, HistoryFilter())).join()
                assertTrue(savedFiles.getValue("history.csv").startsWith("\uFEFF\"field7\""))
                requireNotNull(controller.export("empty.csv", true, HistoryFilter("absent seller"))).join()
                assertEquals(1, savedFiles.getValue("empty.csv").trim().lines().size)
                val request = StatisticsRequest()
                val stats = StatisticsState(request, database.statistics(request), loading = false)
                assertTrue(controller.prepareStatisticsExport(stats))
                requireNotNull(controller.exportStatistics("stats.csv")).join()
                assertTrue(savedFiles.getValue("stats.csv").contains("Summary"))
                requireNotNull(controller.backup("backup.json")).join()
                assertTrue(savedFiles.getValue("backup.json").contains("invoiceqr-history-backup"))
                assertNull(controller.transfer.value)
                val backupPath = Path(directory, "restore.json")
                SystemFileSystem.sink(backupPath).buffered().use { it.writeString(savedFiles.getValue("backup.json")) }
                database.scans().clear()
                val backupUri = NSURL.fileURLWithPath(backupPath.toString()).absoluteString!!
                requireNotNull(controller.importBackup(backupUri)).join()
                assertEquals(Res.string.backup_import_success, controller.message.value?.id)
                assertEquals(listOf(1, 0), controller.message.value?.args)
                assertEquals(original.checkedAt, database.scans().all().single().scannedAt)
                requireNotNull(controller.importBackup(backupUri)).join()
                assertEquals(listOf(0, 1), controller.message.value?.args)
                SystemFileSystem.sink(backupPath).buffered().use { it.writeString("invalid JSON") }
                requireNotNull(controller.importBackup(backupUri)).join()
                assertEquals(Res.string.backup_invalid, controller.message.value?.id)
                assertEquals(1, database.scans().count())
                files.saveDocument = { false }
                controller.message.value = null
                requireNotNull(controller.backup("cancelled.json")).join()
                assertNull(controller.message.value)
                assertNull(controller.transfer.value)
            }
        } finally {
            job.cancelAndJoin(); database.close()
            okio.FileSystem.SYSTEM.deleteRecursively(directory.toPath(), mustExist = false)
        }
    }

    @Test fun backupsRoundTripAnnotationsAndInvalidOrCancelledRestoreRollsBack() = runTest {
        val original = Room.inMemoryDatabaseBuilder<ScanDatabase>().buildInvoiceDatabase()
        val restored = Room.inMemoryDatabaseBuilder<ScanDatabase>().buildInvoiceDatabase()
        try {
            val row = ScanEntity.from(ZatcaParser.parse(INVOICE_QR_PAYLOAD), 1_700_000_000_123L)
                .copy(invoiceNumber = "فاتورة ١", category = 1, note = "سطر\nآخر")
            original.scans().insert(row)
            val output = Buffer()
            DatabaseBackup.write(original, output)
            val text = output.readString()
            fun input(value: String = text) = StreamingBackupJsonInput(Buffer().apply { writeString(value) })
            assertEquals(ImportSummary(1, 0), DatabaseBackup.restore(restored, input()))
            val saved = restored.scans().all().single()
            assertEquals(row.copy(id = saved.id), saved)
            assertEquals(ImportSummary(0, 1), DatabaseBackup.restore(restored, input()))
            restored.scans().clear()
            assertFailsWith<InvalidBackupException> {
                DatabaseBackup.restore(restored, input(text.replace("invoiceqr-history-backup", "wrong-format")))
            }
            assertEquals(0, restored.scans().count())
            assertFailsWith<CancellationException> {
                DatabaseBackup.restore(restored, input()) { throw CancellationException("cancel restore") }
            }
            assertEquals(0, restored.scans().count())
        } finally { original.close(); restored.close() }
    }
}

private class TestPreferences : StringPreferences {
    private val values = mutableMapOf<String, String>()
    override fun contains(key: String) = values.containsKey(key)
    override fun get(key: String, fallback: String?) = values[key] ?: fallback
    override fun write(change: StringPreferencesWriter.() -> Unit) {
        change(StringPreferencesWriter { key, value -> if(value == null) values.remove(key) else values[key] = value })
    }
}
