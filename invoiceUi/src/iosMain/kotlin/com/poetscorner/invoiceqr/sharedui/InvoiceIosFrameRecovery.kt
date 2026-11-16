package com.poetscorner.invoiceqr.sharedui

import com.poetscorner.invoiceqr.shared.MultiFrameConsensus
import com.poetscorner.invoiceqr.shared.ZatcaInvoiceParser
import com.poetscorner.invoiceqr.shared.normalizeQrLuma
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class InvoiceIosFrameRecovery {
    private val consensus = MultiFrameConsensus()

    fun reset() = consensus.reset()

    fun offer(pixels: NSData, nowMillis: Long, decode: (NSData) -> String?): String? {
        val size = 512 * 512
        if (pixels.length != size.toULong()) return null
        val input = ByteArray(size)
        input.usePinned { memcpy(it.addressOf(0), pixels.bytes, size.toULong()) }
        val normalized = normalizeQrLuma(input) ?: run {
            reset()
            return null
        }
        return consensus.offer(normalized, nowMillis) { binary ->
            binary.usePinned { bytes ->
                decode(NSData.create(bytes = bytes.addressOf(0), length = size.toULong()))
                    ?.takeIf { ZatcaInvoiceParser.parse(it).error == null }
            }
        }
    }
}
