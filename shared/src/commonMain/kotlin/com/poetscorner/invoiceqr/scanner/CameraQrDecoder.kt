package com.poetscorner.invoiceqr.scanner

import com.poetscorner.invoiceqr.data.ScannerSettings
import com.poetscorner.invoiceqr.domain.ZatcaParser

/** One instance per camera analysis queue. The read callbacks run synchronously. */
class CameraQrDecoder(private val reader: QrReader) {
    private val recovery = MultiFrameQrRecovery()

    fun read(settings: ScannerSettings, width: Int, height: Int, rotation: Int, now: Long,
        readFrame: (QrReadOptions) -> List<QrSymbol>, pixel: (Int, Int) -> Byte): String? {
        if (!settings.multiFrameRecovery) {
            recovery.reset()
            return readFrame(settings.qrReadOptions()).firstOrNull { it.error == null && it.text != null }?.text
        }
        val observed = readFrame(settings.qrReadOptions(maxSymbols = 3, returnErrors = true))
            .filter { it.format == QrFormat.QR_CODE || it.format == QrFormat.MODEL_2 }
        val valid = observed.firstOrNull { it.error == null && it.text != null }?.text
        if (valid != null) {
            recovery.reset()
            return valid
        }
        val first = observed.firstOrNull()
        if (first != null && observed.any { !recovery.samePosition(first.position, it.position) }) {
            recovery.reset()
            return null
        }
        val corners = first?.takeIf { it.error == QrReadError.CHECKSUM || it.error == QrReadError.FORMAT }?.position
        val recovered = runCatching {
            recovery.offer(width, height, rotation, corners, now, settings.cropEnabled, pixel) {
                reader.read(it, settings.qrReadOptions(maxSymbols = 2))
                    .filter { symbol -> symbol.error == null && symbol.text != null }
                    .singleOrNull()?.text
            }
        }.onFailure { recovery.reset() }.getOrNull()
        return recovered?.takeIf { ZatcaParser.parse(it).error == null }
    }
}
