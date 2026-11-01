package com.poetscorner.invoiceqr

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.ui.invoiceShareIntent
import com.poetscorner.invoiceqr.ui.invoiceSummary
import com.poetscorner.invoiceqr.ui.invoiceTimestampFields
import com.poetscorner.invoiceqr.ui.invoiceTimestampTitle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class InvoiceSharingTest {
    private fun context(language: String): Context {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
    }

    @Test fun summaryIncludesOriginalFieldsAndOptionalHashWithoutOtherQrFields() {
        val invoice = ZatcaParser.parse(historyFixture(101001, seller = "Sharing seller", hash = true).raw)
        assertTrue(invoice.passed)
        for (language in listOf("en", "ar")) {
            val ctx = context(language)
            val summary = invoiceSummary(ctx, invoice)
            for (tag in listOf(1, 2, 4, 5, 6)) assertTrue(summary.contains(invoice.value(tag)))
            val timestampLines = invoiceTimestampFields(ctx, invoice.value(3), forSharing = true).map { (title, value) -> "$title: $value" }
            assertEquals(2, timestampLines.size)
            assertTrue(summary.contains(timestampLines.joinToString("\n")))
            assertFalse(summary.contains(invoiceTimestampTitle(ctx, invoice.value(3))))
            assertFalse(summary.contains(invoice.value(3)))
            assertTrue(summary.contains(ctx.getString(R.string.share_phase_two_passed)))
            assertTrue(summary.contains(ctx.getString(R.string.verification_signature) + ": " +
                ctx.getString(R.string.verification_not_checked)))
            assertTrue(summary.contains(ctx.getString(R.string.verification_official) + ": " +
                ctx.getString(R.string.verification_not_checked)))
            assertFalse(summary.contains(invoice.raw))
            for (tag in 7..9) assertFalse(summary.contains(invoice.value(tag)))
            val intent = invoiceShareIntent(ctx, invoice)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("text/plain", intent.type)
            assertEquals(summary, intent.getStringExtra(Intent.EXTRA_TEXT))
            assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
        }
    }

    @Test fun phaseOneOmitsMissingHashAndFailedInvoicesNeverShareAPassedResult() {
        val ctx = context("en")
        val first = ZatcaParser.parse(historyFixture(101002, seller = "Sharing seller", hash = false).raw)
        assertTrue(first.passed)
        val summary = invoiceSummary(ctx, first)
        assertTrue(summary.contains(ctx.getString(R.string.share_phase_one_passed)))
        assertTrue(summary.contains(ctx.getString(R.string.verification_signature) + ": " +
            ctx.getString(R.string.verification_not_present)))
        assertFalse(summary.contains(ctx.getString(R.string.tag_hash)))
        for (phaseTwo in listOf(false, true)) {
            val failed = ZatcaParser.parse(historyFixture(101003, seller = "", hash = phaseTwo).raw)
            assertFalse(failed.passed)
            val failedSummary = invoiceSummary(ctx, failed)
            assertTrue(failedSummary.contains(ctx.getString(R.string.result_failed)))
            assertFalse(failedSummary.contains(ctx.getString(R.string.share_phase_one_passed)))
            assertFalse(failedSummary.contains(ctx.getString(R.string.share_phase_two_passed)))
        }
    }
}
