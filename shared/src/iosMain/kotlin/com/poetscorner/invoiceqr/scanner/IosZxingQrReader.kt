@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.poetscorner.invoiceqr.scanner

import com.poetscorner.invoiceqr.zxing.*
import kotlinx.cinterop.*

object IosZxingQrReader : QrReader {
    override fun read(image: LumaImage, options: QrReadOptions): List<QrSymbol> {
        require(options.maxNumberOfSymbols in 0..255)
        val symbols = image.pixels.usePinned { buffer ->
            invoiceqr_read(buffer.addressOf(0).reinterpret(), image.pixels.size, image.width, image.height,
                image.rotationDegrees, options.tryHarder, options.tryDenoise, options.tryRotate,
                options.tryInvert, options.tryDownscale, options.binarizer.ordinal,
                options.maxNumberOfSymbols, options.returnErrors)
        } ?: error("zxing-cpp could not read the luminance image")
        try {
            return memScoped {
                val coordinates = allocArray<IntVar>(8)
                val format = alloc<IntVar>()
                val error = alloc<IntVar>()
                List(invoiceqr_symbols_count(symbols)) { index ->
                    val text = invoiceqr_symbol_read(symbols, index, coordinates, format.ptr, error.ptr)
                    val decoded = try { text?.toKString() } finally { invoiceqr_text_delete(text) }
                    fun point(offset: Int) = QrPoint(coordinates[offset], coordinates[offset + 1])
                    QrSymbol(decoded, when (format.value) {
                        1 -> QrFormat.QR_CODE
                        2 -> QrFormat.MODEL_1
                        3 -> QrFormat.MODEL_2
                        else -> QrFormat.OTHER
                    }, QrPosition(point(0), point(2), point(4), point(6)), when (error.value) {
                        0 -> null
                        1 -> QrReadError.FORMAT
                        2 -> QrReadError.CHECKSUM
                        else -> QrReadError.OTHER
                    })
                }
            }
        } finally {
            invoiceqr_symbols_delete(symbols)
        }
    }
}
