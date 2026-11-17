package com.poetscorner.invoiceqr

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.ReviewRepository
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.data.SettingsRepository
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreCompatibilityTest {
    @Test fun opensOriginalScannerAndReviewFilesWithoutLosingKeys() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = createTempDirectory(app.cacheDir.toPath(), "datastore-upgrade-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext() = this
            override fun getFilesDir(): File = directory
        }
        val scanner = context.preferencesDataStoreFile("scanner_settings")
        val review = context.preferencesDataStoreFile("review_usage")
        val retiredEngine = stringPreferencesKey("scan_engine")
        val oldTheme = stringPreferencesKey("supporter_theme")
        val appTheme = stringPreferencesKey("app_theme")
        val faster = stringPreferencesKey("speed")
        val sent = booleanPreferencesKey("attempted")
        val firstUsed = longPreferencesKey("first_used_at")
        val activeDays = stringSetPreferencesKey("active_days")
        val invoices = stringSetPreferencesKey("invoice_keys")
        val futureKey = stringPreferencesKey("future_key")
        try {
            val legacyJob = SupervisorJob()
            val legacyScope = CoroutineScope(Dispatchers.IO + legacyJob)
            try {
                PreferenceDataStoreFactory.create(scope = legacyScope) { scanner }.edit {
                    it[retiredEngine] = "ML_KIT"
                    it[oldTheme] = "PURPLE"
                    it[faster] = "FAST"
                    it[booleanPreferencesKey("harder")] = false
                    it[booleanPreferencesKey("crop_enabled")] = false
                    it[futureKey] = "preserve"
                }
                PreferenceDataStoreFactory.create(scope = legacyScope) { review }.edit {
                    it[sent] = true
                    it[firstUsed] = 1_700_000_000_000L
                    it[activeDays] = setOf("2023-11-14", "2023-11-15", "2023-11-16")
                    it[invoices] = (1..10).map { number -> "hash-$number" }.toSet()
                }
            } finally {
                legacyJob.cancelAndJoin()
            }

            val expected = ScannerSettings(harder = false, speed = "FAST", cropEnabled = false, appTheme = "PURPLE")
            val first = SettingsRepository(context)
            val second = SettingsRepository(context)
            assertEquals(expected, first.settings.first())
            assertEquals(expected, second.settings.first())
            first.save(expected.copy(torch = true))
            assertEquals(expected.copy(torch = true), second.settings.first())

            val usage = ReviewRepository(context)
            usage.recordUse(1_800_000_000_000L)
            assertFalse(usage.claimAttempt(1_800_000_000_000L))

            suspend fun preferences(file: File) = FileSystem.SYSTEM.source(file.absolutePath.toPath()).buffer().use {
                PreferencesSerializer.readFrom(it)
            }
            val scannerValues = preferences(scanner)
            assertEquals("ML_KIT", scannerValues[retiredEngine])
            assertEquals("preserve", scannerValues[futureKey])
            assertEquals("PURPLE", scannerValues[appTheme])
            val reviewValues = preferences(review)
            assertEquals(true, reviewValues[sent])
            assertEquals(1_700_000_000_000L, reviewValues[firstUsed])
            assertEquals(setOf("2023-11-14", "2023-11-15", "2023-11-16"), reviewValues[activeDays])
            assertEquals((1..10).map { "hash-$it" }.toSet(), reviewValues[invoices])
        } finally {
            directory.deleteRecursively()
        }
    }
}
