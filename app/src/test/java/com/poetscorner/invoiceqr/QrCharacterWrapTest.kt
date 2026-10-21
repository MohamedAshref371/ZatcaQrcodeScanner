package com.poetscorner.invoiceqr

import androidx.compose.ui.text.AnnotatedString
import com.poetscorner.invoiceqr.ui.QrCharacterWrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCharacterWrapTest {
    @Test fun everyCharacterCanWrapWithoutChangingTheOriginalQr() {
        val raw = AnnotatedString("AB/c+d==")
        val transformed = QrCharacterWrap.filter(raw)
        assertEquals("A\u200BB\u200B/\u200Bc\u200B+\u200Bd\u200B=\u200B=", transformed.text.text)
        assertEquals("AB/c+d==", raw.text)
        for (start in 0..raw.length) {
            for (end in start..raw.length) {
                val selectedStart = transformed.offsetMapping.transformedToOriginal(
                    transformed.offsetMapping.originalToTransformed(start))
                val selectedEnd = transformed.offsetMapping.transformedToOriginal(
                    transformed.offsetMapping.originalToTransformed(end))
                assertEquals(raw.text.substring(start, end), raw.text.substring(selectedStart, selectedEnd))
            }
        }
        val offsets = (0..transformed.text.length).map(transformed.offsetMapping::transformedToOriginal)
        assertTrue(offsets.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(offsets.all { it in 0..raw.length })
    }

    @Test fun emptyQrAndSingleCharacterHaveValidOffsets() {
        for (raw in listOf("", "/")) {
            val transformed = QrCharacterWrap.filter(AnnotatedString(raw))
            assertEquals(raw, transformed.text.text)
            assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
            assertEquals(raw.length, transformed.offsetMapping.transformedToOriginal(raw.length))
        }
    }
}
