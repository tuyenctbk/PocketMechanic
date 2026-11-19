package com.example.donation

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class BillingTierDisplay(
    val tier: DonationTier,
    val formattedPrice: String,
    val isPlayCatalogLoaded: Boolean
)

data class BillingUiState(
    val isConnecting: Boolean = false,
    val isBillingAvailable: Boolean = true,
    val isBusy: Boolean = false,
    val tiers: List<BillingTierDisplay> = DonationTier.entries.map {
        BillingTierDisplay(it, it.defaultPriceLabel, isPlayCatalogLoaded = false)
    },
    val statusMessage: String? = null,
    val lastPurchasedTierId: String? = null
)

/**
 * Stateless-lifecycle gateway wrapping Google Play BillingClient v9+.
 * All coroutine scopes, lifecycle events, and purchase state live inside [com.example.ui.BillingViewModel].
 */
class BillingManager(
    context: Context
) : PurchasesUpdatedListener {

    private val appContext: Context = context.applicationContext
    private val connectionMutex = Mutex()
    private val productDetailsCache = ConcurrentHashMap<String, ProductDetails>()

    @Volatile
    private var purchaseUpdateHandler: ((BillingResult, List<Purchase>) -> Unit)? = null

    @Volatile
    private var disconnectionHandler: (() -> Unit)? = null

    @Volatile
    private var isClientInitialized = false

    private val billingClient: BillingClient by lazy {
        isClientInitialized = true
        BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }

    fun setListeners(
        onPurchasesUpdated: (BillingResult, List<Purchase>) -> Unit,
        onDisconnected: () -> Unit
    ) {
        this.purchaseUpdateHandler = onPurchasesUpdated
        this.disconnectionHandler = onDisconnected
    }

    fun isReady(): Boolean = isClientInitialized && billingClient.isReady

    fun getCachedProductDetails(productId: String): ProductDetails? = productDetailsCache[productId]

    suspend fun ensureConnected(): Boolean = connectionMutex.withLock {
        if (billingClient.isReady) return true

        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (!cont.isActive) return
                    val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ok) {
                        Log.w(TAG, "Billing setup failed (${billingResult.responseCode}): ${billingResult.debugMessage}")
                    }
                    cont.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    disconnectionHandler?.invoke()
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                }
            })
        }
    }

    suspend fun queryDonationCatalog(): List<BillingTierDisplay> {
        if (!ensureConnected()) {
            return DonationTier.entries.map {
                BillingTierDisplay(it, it.defaultPriceLabel, isPlayCatalogLoaded = false)
            }
        }

        val productList = DonationTier.entries.map { tier ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(tier.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val details = suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
                if (!cont.isActive) return@queryProductDetailsAsync
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(queryResult.productDetailsList)
                } else {
                    Log.w(TAG, "queryProductDetailsAsync failed (${billingResult.responseCode}): ${billingResult.debugMessage}")
                    cont.resume(emptyList())
                }
            }
        }

        productDetailsCache.clear()
        details.forEach { productDetailsCache[it.productId] = it }

        return DonationTier.entries.map { tier ->
            val match = productDetailsCache[tier.productId]
            val price = match?.oneTimePurchaseOfferDetails?.formattedPrice ?: tier.defaultPriceLabel
            BillingTierDisplay(
                tier = tier,
                formattedPrice = price,
                isPlayCatalogLoaded = match != null
            )
        }
    }

    suspend fun queryOwnedInAppPurchases(): List<Purchase> {
        if (!ensureConnected()) return emptyList()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { billingResult, list ->
                if (!cont.isActive) return@queryPurchasesAsync
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(list)
                } else {
                    cont.resume(emptyList())
                }
            }
        }
    }

    suspend fun acknowledgePurchaseIfNeeded(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        if (purchase.isAcknowledged) return true
        if (!ensureConnected()) return false

        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(ackParams) { result ->
                if (!cont.isActive) return@acknowledgePurchase
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    suspend fun launchBillingFlowWithActivity(
        activity: Activity,
        productDetails: ProductDetails
    ): BillingResult? = withContext(Dispatchers.Main.immediate) {
        if (activity.isFinishing || activity.isDestroyed) {
            return@withContext null
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        purchaseUpdateHandler?.invoke(result, purchases?.toList().orEmpty())
    }

    fun release() {
        purchaseUpdateHandler = null
        disconnectionHandler = null
        if (!isClientInitialized) return
        runCatching {
            if (billingClient.isReady) {
                billingClient.endConnection()
            }
        }
    }

    private companion object {
        const val TAG = "BillingManager"
    }
}
