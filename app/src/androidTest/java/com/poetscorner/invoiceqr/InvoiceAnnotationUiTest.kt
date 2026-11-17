package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.openScanDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poetscorner.invoiceqr.data.ScanDatabase
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceAnnotationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editsCompactDetailsAndKeepsThemWhenInvoiceIsReopened() = runBlocking<Unit> {
        val context = compose.activity.applicationContext
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[ScannerViewModel::class.java] }
        val fixture = historyFixture(99201, seller = "Annotation ${System.nanoTime()}")
        var id: Long? = null
        try {
            withContext(Dispatchers.Main) {
                assertTrue(vm.scanned(fixture.raw))
                id = vm.result.value?.scanId
            }
            val edit = compose.activity.getString(R.string.invoice_edit_details)
            compose.onNodeWithText(edit).performClick()
            compose.onNode(hasSetTextAction() and hasText(compose.activity.getString(R.string.invoice_number)))
                .performTextInput("INV-0007")
            compose.onNodeWithTag("invoice_annotation_category").performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.invoice_category_business_purchases)).performClick()
            compose.onNode(hasSetTextAction() and hasText(compose.activity.getString(R.string.invoice_note)))
                .performTextInput("Client visit")
            compose.onNodeWithText(compose.activity.getString(R.string.invoice_save_details)).performClick()
            compose.waitUntil(10_000) { vm.result.value?.annotation?.invoiceNumber == "INV-0007" }
            assertEquals(1, vm.result.value?.annotation?.category)
            assertEquals("Client visit", vm.result.value?.annotation?.note)
            compose.runOnIdle { vm.open(requireNotNull(id)) }
            compose.waitUntil(10_000) { vm.result.value?.annotation?.note == "Client visit" }
            compose.onNodeWithText("INV-0007", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Client visit").assertIsDisplayed()
        } finally {
            val scope = vm.viewModelScope.coroutineContext[Job]!!
            compose.activityRule.scenario.close()
            scope.join()
            val db = openScanDatabase(context)
            try { id?.let { db.scans().delete(it) } } finally { db.close() }
        }
    }
}
