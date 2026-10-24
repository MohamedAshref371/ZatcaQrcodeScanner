package com.poetscorner.invoiceqr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.poetscorner.invoiceqr.scanner.GoogleMlKitModuleInstaller
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.scanner.decoder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Play services client, rather than the controller's fake installer. */
@RunWith(AndroidJUnit4::class)
class MlKitServicesIntegrationTest {
    @Test fun realModuleAvailabilityQueryCompletes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("This integration check requires working Google Play services",
            ConnectionResult.SUCCESS,
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context))
        val installer = GoogleMlKitModuleInstaller(context)
        try {
            // Either Boolean is valid: a fresh device need not have downloaded the module.
            val installed = withTimeout(30_000) { installer.available() }
            println("Real ML Kit module availability: $installed")
        } finally {
            installer.close()
        }
    }

    @Test(timeout = 45_000) fun installedMlKitReadsKnownQrBitmap() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val installer = GoogleMlKitModuleInstaller(instrumentation.targetContext)
        try {
            // This test does not download a module on devices where it is absent.
            assumeTrue("ML Kit module must already be installed for decoding",
                withTimeout(30_000) { installer.available() })
        } finally {
            installer.close()
        }
        // Fixture generated with Python qrcode, payload below, box_size=8 and border=4.
        val bitmap = instrumentation.context.assets.open("play-services-qr.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            ScanEngine.ML_KIT.decoder().use { decoder ->
                assertEquals("invoiceqr-play-services-integration",
                    decoder.read(bitmap, ScannerSettings(scanEngine = ScanEngine.ML_KIT)))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
