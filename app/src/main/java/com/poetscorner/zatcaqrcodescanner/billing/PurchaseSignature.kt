package com.poetscorner.zatcaqrcodescanner.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Play Licensing RSA public key; never grant ownership from an unverified callback. */
object PurchaseSignature {
    fun isValidPublicKey(publicKey: String): Boolean = runCatching {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
    }.isSuccess

    fun verify(publicKey: String, signedData: String, signature: String): Boolean {
        if (publicKey.isBlank() || signedData.isBlank() || signature.isBlank()) return false
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)
    }
}
