package com.cooktime.app

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Client-only, non-consumable lifetime purchase. No entitlement is imported in kitchen backups. */
class ProBilling(context: Context) : PurchasesUpdatedListener {
    companion object { const val PRODUCT = "cooktime_pro_lifetime" }
    private val prefs = context.getSharedPreferences("play_entitlement", Context.MODE_PRIVATE)
    val pro = MutableStateFlow(cachedPurchaseValid())
    val price = MutableStateFlow<String?>(null)
    val message = MutableStateFlow("")
    private var details: ProductDetails? = null
    private var connecting = false
    private val client = BillingClient.newBuilder(context.applicationContext).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private fun verified(p: Purchase): Boolean = runCatching {
        if (BuildConfig.PLAY_LICENSE_KEY.isBlank()) return false
        if (org.json.JSONObject(p.originalJson).optString("packageName") != BuildConfig.APPLICATION_ID) return false
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(BuildConfig.PLAY_LICENSE_KEY, Base64.DEFAULT)))
        Signature.getInstance("SHA1withRSA").run { initVerify(key); update(p.originalJson.toByteArray(Charsets.UTF_8)); verify(Base64.decode(p.signature, Base64.DEFAULT)) }
    }.getOrDefault(false)
    private fun cachedPurchaseValid(): Boolean = runCatching {
        val p = Purchase(prefs.getString("json", "")!!, prefs.getString("signature", "")!!)
        p.purchaseState == Purchase.PurchaseState.PURCHASED && PRODUCT in p.products && verified(p)
    }.getOrDefault(false)
    fun connect() {
        if(client.isReady) { refresh(); loadProduct(); return }
        if(connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if(result.responseCode == BillingClient.BillingResponseCode.OK) { refresh(); loadProduct() }
                else message.value = "Google Play is unavailable. Your saved kitchen still works offline."
            }
            override fun onBillingServiceDisconnected() { connecting = false }
        })
    }
    private fun loadProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
            details = if(result.responseCode == BillingClient.BillingResponseCode.OK) response.productDetailsList.firstOrNull() else null
            price.value = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
        }
    }
    fun refresh() {
        if(!client.isReady) { connect(); return }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if(result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.filter { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if(owned.isEmpty()) { prefs.edit().clear().apply(); pro.value = false }
                owned.forEach(::process)
                if(purchases.any { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }) message.value = "Payment is pending. Pro unlocks once Google Play confirms it."
            } else message.value = "Could not check purchases. Any verified offline access is preserved."
        }
    }
    fun buy(activity: Activity) {
        if(BuildConfig.PLAY_LICENSE_KEY.isBlank()) { message.value = "Purchases are not configured in this build yet."; return }
        if(!client.isReady) { connect(); message.value = "Connecting to Google Play. Please try again shortly."; return }
        // Always fetch fresh details before launching a purchase.
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
            val fresh = response.productDetailsList.firstOrNull()
            val offer = fresh?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            if(result.responseCode != BillingClient.BillingResponseCode.OK || fresh == null || offer == null) {
                message.value = "This upgrade is not available from Google Play yet."; return@queryProductDetailsAsync
            }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(fresh)
            offer.offerToken?.let { productParams.setOfferToken(it) }
            val params = productParams.build()
            val launched = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
            if(launched.responseCode != BillingClient.BillingResponseCode.OK) message.value = "Could not open the purchase screen. Please try again."
        }
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when(result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::process)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> message.value = "Purchase cancelled. You have not unlocked Pro."
            else -> message.value = "Purchase could not be completed. Please try again."
        }
    }
    private fun process(p: Purchase) {
        if(PRODUCT !in p.products) return
        if(p.purchaseState == Purchase.PurchaseState.PENDING) { message.value = "Payment pending. Pro will unlock after confirmation."; return }
        if(p.purchaseState != Purchase.PurchaseState.PURCHASED || !verified(p)) { message.value = "Purchase verification failed. Please restore again when online."; return }
        prefs.edit().putString("json", p.originalJson).putString("signature", p.signature).apply()
        pro.value = true
        if(!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { result ->
            message.value = if(result.responseCode == BillingClient.BillingResponseCode.OK) "Welcome to CookTime Pro" else "Pro is unlocked; purchase confirmation will retry when you reopen the app."
        }
        else message.value = "CookTime Pro restored"
    }
    fun close() = client.endConnection()
}
