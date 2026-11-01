package com.poetscorner.invoiceqr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageImportTest {
    @Test(timeout = 40_000) fun multipleImagesProduceOneSummaryAndContinueAfterBadImage() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScannerViewModel(app, SavedStateHandle()) as T
        })[ScannerViewModel::class.java]
        val first = File.createTempFile("invoiceqr-first-", ".png", app.cacheDir)
        val second = File.createTempFile("invoiceqr-second-", ".png", app.cacheDir)
        val missing = File(app.cacheDir, "invoiceqr-missing-${System.nanoTime()}.png")
        try {
            instrumentation.context.assets.open("play-services-qr.png").use { asset ->
                first.outputStream().use { output -> asset.copyTo(output) }
            }
            instrumentation.context.assets.open("play-services-qr.png").use { asset ->
                second.outputStream().use { output -> asset.copyTo(output) }
            }
            vm.galleryImages(listOf(Uri.fromFile(first), Uri.fromFile(missing), Uri.fromFile(second)))
            val summary = withTimeout(25_000) { vm.imageImportSummary.filterNotNull().first() }
            assertEquals(0, summary.added)
            assertEquals(2, summary.invalidQr)
            assertEquals(1, summary.unreadable)
            assertFalse(summary.interrupted)
            assertEquals(2, vm.screen.value)
        } finally {
            store.clear()
            first.delete()
            second.delete()
        }
    }
}
