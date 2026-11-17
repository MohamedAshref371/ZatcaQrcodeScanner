package com.poetscorner.invoiceqr.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

data class ScannerSettings(val harder: Boolean = true, val denoise: Boolean = false,
    val rotate: Boolean = true, val inverted: Boolean = false, val binarizer: String = "LOCAL_AVERAGE",
    val resolution: String = "HD", val speed: String = "NORMAL", val torch: Boolean = false,
    val theme: String = "SYSTEM", val goldenTheme: Boolean = false,
    val cropEnabled: Boolean = true,
    val appTheme: String = if (goldenTheme) "GOLD" else "DEFAULT",
    val lockPortrait: Boolean = false, val multiFrameRecovery: Boolean = false) {
    val intervalMs get() = when (speed) { "FAST" -> 100L; "FASTEST" -> 50L; else -> 300L }
}
class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        ScannerSettings(p[booleanPreferencesKey("harder")] ?: true, p[booleanPreferencesKey("denoise")] ?: false,
            p[booleanPreferencesKey("rotate")] ?: true, p[booleanPreferencesKey("inverted")] ?: false,
            p[stringPreferencesKey("binarizer")] ?: "LOCAL_AVERAGE", p[stringPreferencesKey("resolution")] ?: "HD",
            p[stringPreferencesKey("speed")] ?: "NORMAL", p[booleanPreferencesKey("torch")] ?: false,
            p[stringPreferencesKey("theme")] ?: "SYSTEM",
            p[booleanPreferencesKey("goldenTheme")] ?: false,
            p[booleanPreferencesKey("crop_enabled")] ?: true,
            p[stringPreferencesKey("app_theme")] ?: p[stringPreferencesKey("supporter_theme")]
                ?: if (p[booleanPreferencesKey("goldenTheme")] == true) "GOLD" else "DEFAULT",
            p[booleanPreferencesKey("lock_portrait")] ?: false,
            p[booleanPreferencesKey("multi_frame_recovery")] ?: false)
    }
    suspend fun save(s: ScannerSettings) { store.edit { p ->
        mapOf("harder" to s.harder, "denoise" to s.denoise, "rotate" to s.rotate,
            "inverted" to s.inverted, "torch" to s.torch,
            "goldenTheme" to s.goldenTheme, "crop_enabled" to s.cropEnabled,
            "lock_portrait" to s.lockPortrait, "multi_frame_recovery" to s.multiFrameRecovery)
            .forEach { (k,v) -> p[booleanPreferencesKey(k)] = v }
        mapOf("binarizer" to s.binarizer, "resolution" to s.resolution, "speed" to s.speed, "theme" to s.theme,
            "app_theme" to s.appTheme)
            .forEach { (k,v) -> p[stringPreferencesKey(k)] = v }
        p.remove(stringPreferencesKey("supporter_theme"))
    } }
}
