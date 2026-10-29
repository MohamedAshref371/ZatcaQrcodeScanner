package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.ui.portraitLockApplies
import org.junit.Assert.*
import org.junit.Test

class OrientationPolicyTest {
    @Test fun portraitLockIsOptIn() {
        assertFalse(ScannerSettings().lockPortrait)
        assertFalse(portraitLockApplies(false, 360, false))
        assertTrue(portraitLockApplies(true, 360, false))
    }

    @Test fun largeScreensAndMultiWindowStayUnrestricted() {
        assertTrue(portraitLockApplies(true, 599, false))
        assertFalse(portraitLockApplies(true, 600, false))
        assertFalse(portraitLockApplies(true, 840, false))
        assertFalse(portraitLockApplies(true, 360, true))
        assertFalse(portraitLockApplies(true, 0, false))
    }
}
