package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.billing.PurchaseSignature
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PurchaseSignatureTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val receipt = """{"productId":"supporter_pack","purchaseState":0,"purchaseToken":"test-token"}"""
    private fun sign(data: String) = Base64.getEncoder().encodeToString(Signature.getInstance("SHA1withRSA").run {
        initSign(keys.private); update(data.toByteArray(Charsets.UTF_8)); sign()
    })
    @Test fun authenticReceiptPassesVerification() {
        assertTrue(PurchaseSignature.isValidPublicKey(publicKey))
        assertTrue(PurchaseSignature.verify(publicKey, receipt, sign(receipt)))
    }
    @Test fun tamperedReceiptCannotUnlockSupporterPack() {
        assertFalse(PurchaseSignature.verify(publicKey, receipt.replace("test-token", "tampered"), sign(receipt)))
    }
    @Test fun receiptSignedByAnotherKeyIsRejected() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(PurchaseSignature.verify(Base64.getEncoder().encodeToString(other.public.encoded), receipt, sign(receipt)))
    }
    @Test fun missingOrMalformedVerificationInputsAreRejected() {
        assertFalse(PurchaseSignature.isValidPublicKey(""))
        assertFalse(PurchaseSignature.isValidPublicKey("AQID"))
        assertFalse(PurchaseSignature.isValidPublicKey(Base64.getEncoder().encodeToString(keys.private.encoded)))
        assertFalse(PurchaseSignature.verify("", receipt, sign(receipt)))
        assertFalse(PurchaseSignature.verify(publicKey, "", sign(receipt)))
        assertFalse(PurchaseSignature.verify(publicKey, receipt, ""))
        assertFalse(PurchaseSignature.verify("not a key", receipt, "not a signature"))
        assertFalse(PurchaseSignature.verify(publicKey, receipt, "%%%"))
    }
}
