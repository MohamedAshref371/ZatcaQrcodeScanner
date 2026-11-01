package com.poetscorner.invoiceqr

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.ui.VerificationLevels
import com.poetscorner.invoiceqr.ui.theme.ZatcaQrcodeScannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationLevelsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun phaseOneShowsAbsentAdvancedFieldsAndNoOfficialVerification() {
        val invoice = ZatcaParser.parse(historyFixture(104001, hash = false).raw)
        compose.setContent { ZatcaQrcodeScannerTheme { VerificationLevels(invoice) } }
        compose.onNodeWithText(context.getString(R.string.verification_levels)).assertIsDisplayed()
            .performClick()
        compose.onAllNodesWithText(context.getString(R.string.verification_not_present))
            .assertCountEquals(2)
        compose.onAllNodesWithText(context.getString(R.string.verification_not_checked))
            .assertCountEquals(1)
    }

    @Test fun failedCoreFieldsAreMarkedForReview() {
        val invoice = ZatcaParser.parse(historyFixture(104002, seller = "", hash = true).raw)
        compose.setContent { ZatcaQrcodeScannerTheme { VerificationLevels(invoice) } }
        compose.onNodeWithText(context.getString(R.string.verification_levels)).performClick()
        compose.onNodeWithText(context.getString(R.string.verification_local_failed)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.verification_not_checked))
            .assertCountEquals(2)
    }
}
