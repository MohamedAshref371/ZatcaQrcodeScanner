package com.poetscorner.invoiceqr.scanner

/** A brief decode miss must not rearm a visible QR, including across preview resizes. */
internal class QrDeliveryGate(private val continuous: Boolean) {
    private var candidate: String? = null
    private var deliveredAt = 0L
    private var absentSince: Long? = null

    @Synchronized fun shouldDeliver(raw: String?, now: Long): Boolean {
        if (!continuous) {
            if (raw == null || (raw == candidate && now - deliveredAt < 4000L)) return false
            candidate = raw
            deliveredAt = now
            return true
        }
        if (raw == null) {
            if (absentSince == null) absentSince = now
            if (now - requireNotNull(absentSince) >= 750L) candidate = null
            return false
        }
        if (absentSince?.let { now - it >= 750L } == true) candidate = null
        absentSince = null
        val normalized = raw.filterNot { it.isWhitespace() }.trimEnd('=')
        if (candidate == normalized) return false
        candidate = normalized
        return true
    }
}
