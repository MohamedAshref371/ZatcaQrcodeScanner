package com.poetscorner.invoiceqr

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Publishing tool: run explicitly on an isolated emulator with -PisolatedValidation=true. */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotCapture {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureStoreScreens() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".validation")) {
            "Capture only in the isolated validation app; never use a user's invoice history."
        }
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        val arabic = compose.activity.resources.configuration.locales[0].language == "ar"
        val language = if (arabic) "ar" else "en"
        val output = File(compose.activity.getExternalFilesDir(null), "store-captures/$language").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cmd statusbar collapse")).use { it.readBytes() }
            Thread.sleep(800) // Allow system bars, camera and theme transitions to settle.
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        withContext(Dispatchers.Main) { vm.update { it.copy(theme = "LIGHT") } }
        compose.waitUntil(10000) { vm.settings.value.theme == "LIGHT" }
        withContext(Dispatchers.Main) { vm.navigate(1) }
        Thread.sleep(6000) // Let the system's camera privacy indicator disappear.
        capture("06-manual-entry")

        val sellers = if (arabic) listOf("متجر تجريبي", "مقهى تجريبي", "مكتبة تجريبية")
            else listOf("Sample Store", "Sample Cafe", "Sample Bookshop")
        for (index in sellers.indices.reversed()) {
            val seller = sellers[index]
            val values = listOf(1 to seller, 2 to "300000000000003", 3 to "2026-10-${20 - index}T10:30:00+03:00",
                4 to listOf("230.00", "57.50", "115.00")[index],
                5 to listOf("30.00", "7.50", "15.00")[index])
            val bytes = values.fold(byteArrayOf()) { all, (tag, text) ->
                val data = text.toByteArray(Charsets.UTF_8)
                all + byteArrayOf(tag.toByte(), data.size.toByte()) + data
            }
            withContext(Dispatchers.Main) { check(vm.scanned(Base64.getEncoder().encodeToString(bytes))) }
        }
        compose.onNodeWithTag("result_list").assertExists()
        capture("01-result")
        compose.onNodeWithTag("result_list").performScrollToNode(hasText(compose.activity.getString(R.string.tag_tax)))
        capture("02-invoice-details")
        compose.onNodeWithTag("result_list").performScrollToIndex(0)
        withContext(Dispatchers.Main) { vm.update { it.copy(theme = "DARK") } }
        compose.waitUntil(10000) { vm.settings.value.theme == "DARK" }
        capture("05-dark-mode")
        withContext(Dispatchers.Main) { vm.update { it.copy(theme = "LIGHT") }; vm.navigate(2) }
        compose.waitUntil(10000) { !vm.historyState.value.loading && vm.historyState.value.total == 3 }
        capture("03-history")
        compose.onNodeWithText(compose.activity.getString(R.string.nav_settings)).performClick()
        compose.onNodeWithTag("settings_list").performScrollToIndex(14)
        compose.onNodeWithText(compose.activity.getString(R.string.backup_import)).assertIsDisplayed()
        capture("04-backup")
    }
}
