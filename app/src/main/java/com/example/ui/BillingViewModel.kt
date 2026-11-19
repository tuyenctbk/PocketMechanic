package com.example.ui

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.example.analytics.AppEventCategory
import com.example.analytics.AppEventNames
import com.example.analytics.AppEventTracker
import com.example.donation.BillingManager
import com.example.donation.BillingUiState
import com.example.donation.DonationTier
import com.example.engagement.EngagementPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Lifecycle-aware ViewModel managing the [BillingManager] connection inside [viewModelScope],
 * refreshing pending/restored purchases in [onResume], and safely handling [Activity] sessions
 * via [WeakReference] + live lifecycle checks without leaking any Activity context.
 */
class BillingViewModel(
    private val billingManager: BillingManager,
    private val preferencesRepo: EngagementPreferencesRepository,
    private val eventTracker: AppEventTracker,
    private val userId: String
) : ViewModel(), DefaultLifecycleObserver {

    private val _billingState = MutableStateFlow(BillingUiState())
    val billingState: StateFlow<BillingUiState> = _billingState.asStateFlow()

    init {
        billingManager.setListeners(
            onPurchasesUpdated = { result, purchases ->
                handlePurchasesUpdated(result, purchases)
            },
            onDisconnected = {
                _billingState.update { it.copy(isConnecting = false) }
            }
        )
        initializeCatalogAndRestore()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        refreshCatalogAndRestoreIfNeeded()
    }

    fun clearStatusMessage() {
        _billingState.update { it.copy(statusMessage = null) }
    }

    fun initializeCatalogAndRestore() {
        viewModelScope.launch {
            _billingState.update { it.copy(isConnecting = true) }
            val connected = billingManager.ensureConnected()
            if (!connected) {
                _billingState.update {
                    it.copy(
                        isConnecting = false,
                        isBillingAvailable = false
                    )
                }
                return@launch
            }
            val tiers = billingManager.queryDonationCatalog()
            _billingState.update {
                it.copy(
                    isConnecting = false,
                    isBillingAvailable = true,
                    tiers = tiers
                )
            }
            restoreOwnedPurchases()
        }
    }

    private fun refreshCatalogAndRestoreIfNeeded() {
        viewModelScope.launch {
            if (!billingManager.isReady()) {
                val connected = billingManager.ensureConnected()
                _billingState.update { it.copy(isBillingAvailable = connected) }
                if (!connected) return@launch
                val tiers = billingManager.queryDonationCatalog()
                _billingState.update { it.copy(tiers = tiers) }
            }
            restoreOwnedPurchases()
        }
    }

    fun launchPurchase(activity: Activity?, tier: DonationTier) {
        val initialActivity = activity?.takeIf { !it.isFinishing && !it.isDestroyed }
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.DONATION_PURCHASE_INITIATED,
            category = AppEventCategory.MONETIZATION,
            properties = mapOf(
                "productId" to tier.productId,
                "hasActivity" to (initialActivity != null)
            )
        )

        if (initialActivity == null) {
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.DONATION_FAILED,
                category = AppEventCategory.MONETIZATION,
                properties = mapOf("reason" to "missing_activity_session")
            )
            _billingState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = "An active screen session is required to open Google Play Billing."
                )
            }
            return
        }

        val activityRef = WeakReference(initialActivity)

        viewModelScope.launch {
            _billingState.update { it.copy(isBusy = true, statusMessage = null) }

            val connected = billingManager.ensureConnected()
            if (!connected) {
                eventTracker.track(
                    userId = userId,
                    eventName = AppEventNames.DONATION_FAILED,
                    category = AppEventCategory.MONETIZATION,
                    properties = mapOf("reason" to "billing_unavailable")
                )
                _billingState.update {
                    it.copy(
                        isBusy = false,
                        isBillingAvailable = false,
                        statusMessage = "Google Play Billing is unavailable on this device. You can use PayPal QR or Ko-fi below."
                    )
                }
                return@launch
            }

            if (billingManager.getCachedProductDetails(tier.productId) == null) {
                val refreshedTiers = billingManager.queryDonationCatalog()
                _billingState.update { it.copy(tiers = refreshedTiers) }
            }

            val details = billingManager.getCachedProductDetails(tier.productId)
            if (details == null) {
                eventTracker.track(
                    userId = userId,
                    eventName = AppEventNames.DONATION_FAILED,
                    category = AppEventCategory.MONETIZATION,
                    properties = mapOf("reason" to "sku_not_loaded", "productId" to tier.productId)
                )
                _billingState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Play Store product '${tier.productId}' is not active on this test build yet. Use PayPal QR or Ko-fi for instant support."
                    )
                }
                return@launch
            }

            val liveActivity = activityRef.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
            if (liveActivity == null) {
                _billingState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Screen closed before Play checkout opened. Please try again."
                    )
                }
                return@launch
            }

            val launchResult = billingManager.launchBillingFlowWithActivity(liveActivity, details)
            if (launchResult == null) {
                _billingState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Activity session ended before checkout could start."
                    )
                }
            } else if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                eventTracker.track(
                    userId = userId,
                    eventName = AppEventNames.DONATION_FAILED,
                    category = AppEventCategory.MONETIZATION,
                    properties = mapOf(
                        "code" to launchResult.responseCode,
                        "message" to launchResult.debugMessage
                    )
                )
                _billingState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = launchResult.debugMessage.ifBlank {
                            "Google Play could not launch checkout (code ${launchResult.responseCode})."
                        }
                    )
                }
            }
        }
    }

    private fun handlePurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isEmpty()) {
                    _billingState.update { it.copy(isBusy = false) }
                    return
                }
                viewModelScope.launch {
                    purchases.forEach { processPurchase(it) }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.update {
                    it.copy(isBusy = false, statusMessage = "Checkout canceled.")
                }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                viewModelScope.launch {
                    restoreOwnedPurchases()
                    _billingState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Supporter tier already owned — Active Supporter status restored!"
                        )
                    }
                }
            }

            else -> {
                eventTracker.track(
                    userId = userId,
                    eventName = AppEventNames.DONATION_FAILED,
                    category = AppEventCategory.MONETIZATION,
                    properties = mapOf(
                        "code" to result.responseCode,
                        "message" to result.debugMessage
                    )
                )
                _billingState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = result.debugMessage.ifBlank {
                            "Google Play Billing returned code ${result.responseCode}."
                        }
                    )
                }
            }
        }
    }

    private suspend fun restoreOwnedPurchases() {
        val owned = billingManager.queryOwnedInAppPurchases()
        owned.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                processPurchase(purchase, isRestore = true)
            }
        }
    }

    private suspend fun processPurchase(purchase: Purchase, isRestore: Boolean = false) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val ackOk = billingManager.acknowledgePurchaseIfNeeded(purchase)
                if (!ackOk) {
                    if (!isRestore) {
                        _billingState.update {
                            it.copy(
                                isBusy = false,
                                statusMessage = "Purchase received; confirmation pending with Google Play."
                            )
                        }
                    }
                    return
                }
                val tierId = purchase.products.firstOrNull() ?: DonationTier.TIER_1.productId
                val currentPrefs = preferencesRepo.currentState()
                val needsPersist = !currentPrefs.isActiveSupporter || currentPrefs.supporterTierId != tierId
                if (needsPersist) {
                    preferencesRepo.setActiveSupporter(isSupporter = true, tierId = tierId)
                }

                if (!isRestore) {
                    eventTracker.track(
                        userId = userId,
                        eventName = AppEventNames.DONATION_COMPLETED,
                        category = AppEventCategory.MONETIZATION,
                        properties = mapOf("channel" to "google_play", "tierId" to tierId)
                    )
                    _billingState.update {
                        it.copy(
                            isBusy = false,
                            lastPurchasedTierId = tierId,
                            statusMessage = "Thank you! Active Supporter badge unlocked."
                        )
                    }
                } else {
                    _billingState.update {
                        it.copy(
                            isBusy = false,
                            lastPurchasedTierId = it.lastPurchasedTierId ?: tierId
                        )
                    }
                }
            }

            Purchase.PurchaseState.PENDING -> {
                if (!isRestore) {
                    _billingState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Payment pending confirmation. Your supporter badge will activate automatically once settled."
                        )
                    }
                }
            }

            else -> {
                if (!isRestore) {
                    _billingState.update { it.copy(isBusy = false) }
                }
            }
        }
    }

    override fun onCleared() {
        billingManager.release()
        super.onCleared()
    }
}
