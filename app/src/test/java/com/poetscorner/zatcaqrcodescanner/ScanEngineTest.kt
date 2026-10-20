package com.poetscorner.zatcaqrcodescanner

import com.poetscorner.zatcaqrcodescanner.data.ScanEngine
import com.poetscorner.zatcaqrcodescanner.data.ScannerSettings
import org.junit.Assert.*
import org.junit.Test

class ScanEngineTest {
    @Test fun existingInstallationsAndUnknownPreferencesKeepZxing() {
        assertEquals(ScanEngine.ZXING, ScannerSettings().scanEngine)
        assertEquals(ScanEngine.ZXING, ScanEngine.fromPreference(null))
        assertEquals(ScanEngine.ZXING, ScanEngine.fromPreference("unknown"))
    }

    @Test fun bothEnginesRoundTripThroughTheirStoredNames() {
        ScanEngine.entries.forEach { assertEquals(it, ScanEngine.fromPreference(it.name)) }
        assertEquals(ScanEngine.ML_KIT, ScannerSettings().copy(scanEngine = ScanEngine.ML_KIT).scanEngine)
    }
}
