package com.poetscorner.invoiceqr

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedImagesTest {
    private val first = Uri.parse("content://example.provider/first")
    private val second = Uri.parse("content://example.provider/second")

    @Test fun acceptsSingleAndMultipleSharedFilesWithoutDoubleProcessingClipData() {
        val single = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, first)
        }
        assertEquals(listOf(first), single.sharedInvoiceUris())

        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newRawUri("shared", first).apply {
                addItem(ClipData.Item(second))
            }
        }
        assertEquals(listOf(first, second), multiple.sharedInvoiceUris())

        val pdf = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, first)
        }
        assertEquals(listOf(first), pdf.sharedInvoiceUris())

        val mixed = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }
        assertEquals(listOf(first, second), mixed.sharedInvoiceUris())
    }

    @Test fun ignoresTextWithoutFilesAndOtherActions() {
        val text = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "hello")
        }
        assertTrue(text.sharedInvoiceUris().isEmpty())
        val view = Intent(Intent.ACTION_VIEW).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, first)
        }
        assertTrue(view.sharedInvoiceUris().isEmpty())
    }

    @Test fun pickerReturnsSelectedSingleOrMixedUrisOnlyOnce() {
        val result = Intent().apply {
            data = first
            clipData = ClipData.newRawUri("picked", first).apply { addItem(ClipData.Item(second)) }
        }
        assertEquals(listOf(first, second), result.selectedInvoiceUris())
    }
}
