package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ScannerSettings
import org.junit.Assert.assertFalse
import org.junit.Test

class ScannerSettingsDefaultsTest {
    @Test fun portraitLockAndMultiFrameRecoveryAreOptIn() {
        val settings = ScannerSettings()
        assertFalse(settings.lockPortrait)
        assertFalse(settings.multiFrameRecovery)
    }
}
