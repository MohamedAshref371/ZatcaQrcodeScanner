package com.poetscorner.invoiceqr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Run each stage explicitly in a separate process, with the library upgrade between 1 and 2. */
@RunWith(AndroidJUnit4::class)
class DataStoreUpgradeTest {
    private val repository get() = SettingsRepository(
        InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun seedWithOldLibrary() = runBlocking {
        requireStage("seed")
        withTimeout(15_000) {
            repository.save(legacySettings)
            assertEquals(legacySettings, repository.settings.first())
        }
    }

    @Test fun readOldSettingsAndSaveNewChoices() = runBlocking {
        requireStage("upgrade")
        withTimeout(15_000) {
            assertEquals("The new library must preserve all old settings", legacySettings,
                repository.settings.first())
            repository.save(updatedSettings)
            assertEquals(updatedSettings, repository.settings.first())
        }
    }

    @Test fun readNewChoicesAfterProcessRestart() = runBlocking {
        requireStage("restart")
        withTimeout(15_000) {
            assertEquals("Saved choices must survive a process restart", updatedSettings,
                repository.settings.first())
        }
    }

    private fun requireStage(stage: String) {
        org.junit.Assume.assumeTrue("Run with -e settingsUpgradeStage $stage",
            InstrumentationRegistry.getArguments().getString("settingsUpgradeStage") == stage)
    }

    private companion object {
        val legacySettings = ScannerSettings(harder = false, denoise = true, rotate = false,
            inverted = true, binarizer = "GLOBAL_HISTOGRAM", resolution = "FHD", speed = "FAST",
            torch = false, tapFocus = false, theme = "DARK", goldenTheme = false,
            scanEngine = ScanEngine.ML_KIT)
        val updatedSettings = ScannerSettings(harder = true, denoise = false, rotate = true,
            inverted = false, binarizer = "LOCAL_AVERAGE", resolution = "HD", speed = "NORMAL",
            torch = false, tapFocus = true, theme = "LIGHT", goldenTheme = false,
            scanEngine = ScanEngine.ZXING)
    }
}
