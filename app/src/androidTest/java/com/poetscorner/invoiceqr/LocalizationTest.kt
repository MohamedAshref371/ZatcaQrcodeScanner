package com.poetscorner.invoiceqr

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.domain.ZatcaParser
import com.poetscorner.invoiceqr.domain.asUiText
import com.poetscorner.invoiceqr.ui.text.UiText
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private fun context(language: String): Context {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(app.resources.configuration).apply {
            setLocales(LocaleList(locale)); setLayoutDirection(locale)
        }
        return app.createConfigurationContext(config)
    }

    @Test fun languageControlsNamesDirectionAndFallback() {
        val ar = context("ar-SA")
        val en = context("en-US")
        assertEquals("الماسح", ar.getString(R.string.nav_scanner))
        assertEquals("Scanner", en.getString(R.string.nav_scanner))
        assertEquals(View.LAYOUT_DIRECTION_RTL, ar.resources.configuration.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, en.resources.configuration.layoutDirection)
        assertEquals("Scanner", context("fr-FR").getString(R.string.nav_scanner))
    }

    @Test fun retainedChecksAndErrorsResolveInTheCurrentLanguage() {
        val check = UiText(R.string.check_present, UiText(R.string.tag_seller))
        assertEquals("Seller name is present", check.resolve(context("en")))
        assertEquals("وجود اسم البائع", check.resolve(context("ar")))
        val bad = ZatcaParser.parse("https://example.org")
        assertEquals(context("en").getString(R.string.error_base64), bad.error!!.asUiText().resolve(context("en")))
        assertEquals(context("ar").getString(R.string.error_base64), bad.error!!.asUiText().resolve(context("ar")))
        val formatted = UiText(R.string.error_tag_duplicate, 6)
        assertTrue(formatted.resolve(context("en")).contains("6"))
    }

    @Test fun everyEnglishResourceHasNoArabicFallback() {
        val en = context("en")
        R.string::class.java.declaredFields.filter { it.type == Int::class.javaPrimitiveType }.forEach { field ->
            val text = en.getString(field.getInt(null))
            assertTrue(field.name, text.isNotBlank())
            // Language names intentionally retain their native spelling in the picker.
            if (field.name != "language_arabic") {
                assertFalse(field.name, text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC })
            }
        }
    }
}
