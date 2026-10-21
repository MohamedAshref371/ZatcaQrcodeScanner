package com.poetscorner.invoiceqr

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.scanner.*
import com.poetscorner.invoiceqr.ui.ScanEnginePicker
import com.poetscorner.invoiceqr.ui.theme.ZatcaQrcodeScannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanEnginePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun downloadIsSeparateFromSelectionAndChoiceUnlocksOnlyWhenReady() {
        val module = mutableStateOf(MlKitModuleState(MlKitModuleStatus.NOT_INSTALLED))
        var downloads = 0
        var selected = ScanEngine.ZXING
        compose.setContent {
            ZatcaQrcodeScannerTheme {
                ScanEnginePicker(selected, module.value, { selected = it }, { downloads++ })
            }
        }
        compose.onNodeWithTag("mlkit_option").assertIsNotEnabled()
        compose.onNodeWithTag("mlkit_download").performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(ScanEngine.ZXING, selected)
            module.value = MlKitModuleState(MlKitModuleStatus.DOWNLOADING, 0.5f)
        }
        compose.onNodeWithTag("mlkit_download").assertDoesNotExist()
        compose.onNodeWithTag("mlkit_option").assertIsNotEnabled()
        compose.runOnIdle { module.value = MlKitModuleState(MlKitModuleStatus.READY) }
        compose.onNodeWithTag("mlkit_download").assertDoesNotExist()
        compose.onNodeWithTag("mlkit_option").assertIsEnabled().assertIsNotSelected()
        compose.runOnIdle { assertEquals(ScanEngine.ZXING, selected) }
        compose.onNodeWithTag("mlkit_option").performClick()
        compose.runOnIdle { assertEquals(ScanEngine.ML_KIT, selected) }
    }

    @Test fun failedSetupOffersRetryWithoutEnablingMlKit() {
        var retries = 0
        compose.setContent {
            ZatcaQrcodeScannerTheme {
                ScanEnginePicker(ScanEngine.ZXING, MlKitModuleState(MlKitModuleStatus.FAILED), {}, { retries++ })
            }
        }
        compose.onNodeWithTag("mlkit_option").assertIsNotEnabled()
        compose.onNodeWithTag("mlkit_download").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
