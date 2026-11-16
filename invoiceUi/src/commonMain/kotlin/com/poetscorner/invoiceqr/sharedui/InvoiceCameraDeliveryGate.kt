package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.normalizedInvoicePayload

/** AVFoundation reports detected codes. A gap in observations rearms each code independently. */
class InvoiceCameraDeliveryGate {
    private val lastSeen = mutableMapOf<String, Long>()

    fun newlyVisible(rawCodes: List<String>, nowMillis: Long): List<String> {
        lastSeen.entries.removeAll { nowMillis - it.value >= 1_250L }
        return rawCodes.filter { raw ->
            lastSeen.put(normalizedInvoicePayload(raw), nowMillis) == null
        }
    }
}
