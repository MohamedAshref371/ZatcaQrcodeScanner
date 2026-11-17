package com.poetscorner.invoiceqr.data

import java.security.MessageDigest

fun invoicePayloadKey(raw: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(raw.filterNot { it.isWhitespace() }.trimEnd('=').toByteArray(Charsets.US_ASCII))
    val hex = "0123456789abcdef"
    return buildString(64) { digest.forEach { byte ->
        val number = byte.toInt() and 255
        append(hex[number ushr 4]); append(hex[number and 15])
    } }
}
