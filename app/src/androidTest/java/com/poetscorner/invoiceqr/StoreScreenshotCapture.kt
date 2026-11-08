package com.poetscorner.invoiceqr

import android.os.ParcelFileDescriptor.AutoCloseInputStream
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.StatisticsRequest
import com.poetscorner.invoiceqr.data.StatisticsSort
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real frame clock: Compose test-clock rendering is unsuitable for store captures. */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotCapture {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(15_000) {
        while (!condition()) delay(100)
    }

    // Coordinates are based on the original 1080x1920 capture and scaled for tablet AVDs.
    private fun screenSize(): Pair<Int, Int> {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private suspend fun swipe(fromY: Int, toY: Int) {
        val (width, height) = screenSize()
        val x = width / 2
        val start = fromY * height / 1920
        val end = toY * height / 1920
        AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "input swipe $x $start $x $end 800")).use { it.readBytes() }
        delay(400)
    }

    @Test fun captureStoreScreens() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".validation")) {
            "Capture only in the isolated validation app; never use a user's invoice history."
        }
        lateinit var vm: ScannerViewModel
        lateinit var activity: MainActivity
        activityRule.scenario.onActivity {
            activity = it
            vm = ViewModelProvider(it)[ScannerViewModel::class.java]
        }
        val arabic = activity.resources.configuration.locales[0].language == "ar"
        val language = if (arabic) "ar" else "en"
        val output = File(activity.getExternalFilesDir(null), "store-captures/$language").apply { mkdirs() }
        suspend fun navigate(screen: Int) {
            withContext(Dispatchers.Main) { vm.selectTab(screen) }
            delay(600)
        }
        suspend fun capture(name: String) {
            withContext(Dispatchers.Main) { vm.message.value = null }
            instrumentation.waitForIdleSync()
            delay(1500)
            withContext(Dispatchers.Main) { check(activity.window.decorView.isShown) }
            AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("screencap -p")).use { source ->
                File(output, "$name.png").outputStream().use { target -> source.copyTo(target) }
            }
        }
        suspend fun captureBackup() {
            navigate(3)
            repeat(4) { swipe(500, 1500) }
            repeat(if (arabic) 2 else 3) { swipe(1400, 700) }
            if (arabic) swipe(1200, 700) else swipe(1000, 850)
            capture("06-backup")
        }
        if (InstrumentationRegistry.getArguments().getString("storeCaptureOnly") == "backup") {
            withContext(Dispatchers.Main) { vm.update { it.copy(theme = "LIGHT") } }
            waitUntil { vm.settings.value.theme == "LIGHT" }
            captureBackup()
            return@runBlocking
        }
        withContext(Dispatchers.Main) { vm.update { it.copy(theme = "LIGHT") }; vm.clearResult() }
        navigate(1)
        waitUntil { vm.settings.value.theme == "LIGHT" }
        delay(6000)
        capture("08-manual-entry")

        val sellers = if (arabic) listOf("متجر تجريبي", "مقهى تجريبي", "مكتبة تجريبية")
            else listOf("Sample Store", "Sample Cafe", "Sample Bookshop")
        for (index in (0..5).reversed()) {
            val values = listOf(1 to sellers[index % sellers.size], 2 to "300000000000003",
                3 to "2026-10-${20 - index}T10:30:00",
                4 to listOf("230.00", "57.50", "115.00")[index % sellers.size],
                5 to listOf("30.00", "7.50", "15.00")[index % sellers.size])
            val bytes = values.fold(byteArrayOf()) { all, (tag, text) ->
                val data = text.toByteArray(Charsets.UTF_8)
                all + byteArrayOf(tag.toByte(), data.size.toByte()) + data
            }
            withContext(Dispatchers.Main) { check(vm.scanned(Base64.getEncoder().encodeToString(bytes))) }
        }
        capture("02-result")
        swipe(1400, 600)
        capture("03-invoice-details")
        repeat(3) { swipe(600, 1400) }
        withContext(Dispatchers.Main) { vm.update { it.copy(theme = "DARK") } }
        waitUntil { vm.settings.value.theme == "DARK" }
        capture("07-dark-mode")
        withContext(Dispatchers.Main) { vm.update { it.copy(theme = "LIGHT") } }
        navigate(2)
        waitUntil { !vm.historyState.value.loading && vm.historyState.value.total == 6 }
        capture("04-history")
        withContext(Dispatchers.Main) {
            vm.updateStatistics(StatisticsRequest(sort = StatisticsSort.TOTAL, descending = true))
        }
        navigate(4)
        waitUntil { !vm.statistics.value.loading && vm.statistics.value.result.groups.size == 3 }
        capture("05-statistics")
        captureBackup()
        withContext(Dispatchers.Main) { vm.setContinuousScanning(true) }
        navigate(0)
        delay(14_000)
        capture("01-camera")
    }
}
