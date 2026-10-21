package com.poetscorner.invoiceqr.billing

import android.app.Activity
import android.app.Application
import com.poetscorner.invoiceqr.R
import com.poetscorner.invoiceqr.ui.text.UiText
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import com.poetscorner.invoiceqr.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

data class SupporterState(val owned: Boolean = false, val loading: Boolean = false,
    val purchasing: Boolean = false, val pending: Boolean = false, val price: String? = null,
    val available: Boolean = false, val message: UiText? = null)

class SupporterViewModel(app: Application) : AndroidViewModel(app) {
    companion object { const val PRODUCT_ID = "supporter_pack" }
    private val prefs = app.getSharedPreferences("supporter_entitlement", Application.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(SupporterState(owned = prefs.getBoolean("owned", false)))
    val state = mutableState.asStateFlow()
    private val configured = PurchaseSignature.isValidPublicKey(BuildConfig.PLAY_BILLING_PUBLIC_KEY)
    private var details: ProductDetails? = null
    private var connecting = false
    private var querying = false
    private var closed = false
    private var purchaseGeneration = 0L
    private val processing = Mutex()
    private val client: BillingClient = BillingClient.newBuilder(app)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases ->
            purchaseGeneration++
            viewModelScope.launch {
                mutableState.value = state.value.copy(purchasing = false)
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> process(purchases.orEmpty(), false)
                    BillingClient.BillingResponseCode.USER_CANCELED -> mutableState.value = state.value.copy(message = UiText(R.string.billing_cancelled))
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                    else -> failed(UiText(R.string.billing_failed))
                }
            }
        }.build()

    fun refresh() {
        if (closed || state.value.purchasing || state.value.loading || connecting || querying) return
        if (!configured) { failed(UiText(R.string.billing_unconfigured)); return }
        mutableState.value = state.value.copy(loading = true, message = null)
        if (client.isReady) queryStore() else {
            connecting = true
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (closed) return
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) queryStore()
                    else failed(UiText(R.string.billing_connect_error))
                }
                override fun onBillingServiceDisconnected() {
                    connecting = false
                    if (!closed) failed(UiText(R.string.billing_disconnected))
                }
            })
        }
    }

    private fun queryStore() {
        if (closed || querying) return
        querying = true
        val generation = purchaseGeneration
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            viewModelScope.launch {
                querying = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    process(purchases, generation == purchaseGeneration)
                    mutableState.value = state.value.copy(loading = false)
                } else failed(UiText(R.string.billing_restore_error))
            }
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
        )).build()) { result, products ->
            if (!closed) {
                details = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                    products.productDetailsList.firstOrNull { it.productId == PRODUCT_ID } else null
                val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                mutableState.value = state.value.copy(price = offer?.formattedPrice, available = offer != null)
                if (offer == null && !state.value.owned) mutableState.value = state.value.copy(message = UiText(R.string.billing_region_unavailable))
            }
        }
    }

    fun purchase(activity: Activity) {
        if (!configured || state.value.owned || state.value.pending || state.value.purchasing || state.value.loading) return
        val product = details ?: return
        val offer = product.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return
        if (!client.isReady) { refresh(); return }
        mutableState.value = state.value.copy(purchasing = true, message = null)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
            .apply { offer.offerToken?.let { setOfferToken(it) } }.build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.value = state.value.copy(purchasing = false)
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh()
            else failed(UiText(R.string.billing_launch_error))
        }
    }

    private suspend fun process(purchases: List<Purchase>, authoritative: Boolean) = processing.withLock {
        val relevant = purchases.filter { PRODUCT_ID in it.products }
        var verifiedOwner = false
        var invalid = false
        var acknowledgementFailed = false
        for (purchase in relevant.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            val valid = withContext(Dispatchers.Default) {
                PurchaseSignature.verify(BuildConfig.PLAY_BILLING_PUBLIC_KEY, purchase.originalJson, purchase.signature) &&
                    runCatching { JSONObject(purchase.originalJson).getString("packageName") == BuildConfig.APPLICATION_ID }.getOrDefault(false) &&
                    purchase.purchaseToken.isNotBlank()
            }
            if (!valid) { invalid = true; continue }
            verifiedOwner = true
            setOwned(true)
            if (!purchase.isAcknowledged) {
                var acknowledged = false
                for (attempt in 0..2) {
                    val code = suspendCancellableCoroutine<Int> { continuation ->
                        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) {
                            if (continuation.isActive) continuation.resume(it.responseCode)
                        }
                    }
                    if (code == BillingClient.BillingResponseCode.OK) { acknowledged = true; break }
                    if (attempt < 2) delay(1000L shl attempt)
                }
                acknowledgementFailed = acknowledgementFailed || !acknowledged
            }
        }
        if (authoritative && !verifiedOwner && !invalid) setOwned(false)
        mutableState.value = state.value.copy(pending = relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING },
            message = when {
                invalid -> UiText(R.string.billing_verify_error)
                acknowledgementFailed -> UiText(R.string.billing_ack_error)
                verifiedOwner -> UiText(R.string.billing_thanks)
                relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> UiText(R.string.billing_pending_note)
                authoritative -> UiText(R.string.billing_not_owned)
                else -> state.value.message
            })
    }

    private fun setOwned(owned: Boolean) {
        prefs.edit().putBoolean("owned", owned).apply()
        mutableState.value = state.value.copy(owned = owned)
    }
    private fun failed(message: UiText) {
        mutableState.value = state.value.copy(loading = false, available = false, message = message)
    }
    override fun onCleared() { closed = true; client.endConnection() }
}
