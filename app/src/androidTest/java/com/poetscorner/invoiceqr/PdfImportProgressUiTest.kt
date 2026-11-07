package com.poetscorner.invoiceqr

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfImportProgressUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000) fun progressAndCancelAppearBelowTitleOnScanner() {
        val pdf = File.createTempFile("invoiceqr-progress-", ".pdf", compose.activity.cacheDir)
        val document = PdfDocument()
        try {
            repeat(50) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                document.finishPage(page)
            }
            pdf.outputStream().use(document::writeTo)
        } finally { document.close() }

        try {
            val vm = ViewModelProvider(compose.activity)[ScannerViewModel::class.java]
            compose.runOnIdle {
                vm.selectTab(0)
                vm.importInvoiceFiles(listOf(Uri.fromFile(pdf)))
            }
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onAllNodesWithTag("file_import_progress").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            compose.onNodeWithTag("file_import_progress").assertIsDisplayed()
            compose.onNodeWithTag("file_import_cancel").assertIsDisplayed()
            val titleBottom = compose.onNodeWithTag("app_title_bar").fetchSemanticsNode().boundsInRoot.bottom
            val progressTop = compose.onNodeWithTag("file_import_progress").fetchSemanticsNode().boundsInRoot.top
            assertTrue("Import progress must be below the app title", progressTop >= titleBottom)
            compose.onNodeWithTag("file_import_cancel").performClick()
        } finally { pdf.delete() }
    }
}
