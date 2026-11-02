package com.poetscorner.invoiceqr.ui

import java.util.concurrent.ConcurrentLinkedQueue

/** Opt-in timing for device tests. No invoice contents or images are retained. */
internal object CameraFrameTiming {
    data class Sample(val cropEnabled: Boolean, val width: Int, val height: Int,
        val mainAndCropNs: Long, val decodeNs: Long, val foundQr: Boolean)

    @Volatile var enabled = false
    private val samples = ConcurrentLinkedQueue<Sample>()

    fun record(sample: Sample) {
        if (enabled) samples.add(sample)
    }

    fun clear() = samples.clear()
    fun snapshot(): List<Sample> = samples.toList()
}
