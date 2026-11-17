package com.poetscorner.invoiceqr.data

import okio.ByteString.Companion.encodeUtf8

/** Match the original Android SHA-256 key, including its US-ASCII replacement behavior. */
fun invoicePayloadKey(raw: String): String {
    val normalized = raw.filterNot(Char::isWhitespace).trimEnd('=')
    val ascii = buildString(normalized.length) {
        var index = 0
        while (index < normalized.length) {
            val char = normalized[index++]
            append(if (char.code <= 127) char else '?')
            if (char.isHighSurrogate() && index < normalized.length && normalized[index].isLowSurrogate()) index++
        }
    }
    return ascii.encodeUtf8().sha256().hex()
}
