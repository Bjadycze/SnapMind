package com.app.snapmind.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.app.snapmind.data.prefs.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing, wrapped so the rest of the app only ever sees a boolean (spec.md 11.2).
 *
 * The one rule that outranks everything here: **it fails open.** `entitled` stays null until
 * Play actually answers, and a null means "no answer", never "not entitled" -- the cached value
 * from the last successful check is what the app uses in the meantime. Someone who paid must
 * not lose access because Play was unreachable on a train (spec.md, Task 8).
 *
 * Subscription and one-time purchase are both accepted. Which of them exists is decided in
 * Play Console, not here, so adding a lifetime purchase later needs no code change.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** null = Play has not answered yet. Never treat it as "not entitled". */
    private val _entitled = MutableStateFlow<Boolean?>(null)
    val entitled: StateFlow<Boolean?> = _entitled.asStateFlow()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            apply(purchases)
        }
        // Every other code, USER_CANCELED included, leaves the current state alone: a cancelled
        // purchase dialog is not evidence that an existing subscription went away.
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Call on app start and on every return to the foreground. Cheap, and it self-heals. */
    fun refresh() = connected {
        query(BillingClient.ProductType.SUBS) { subs ->
            query(BillingClient.ProductType.INAPP) { inApp -> apply(subs + inApp) }
        }
    }

    /**
     * Opens Play's purchase sheet. Needs an Activity -- Play draws it on top of the caller,
     * so an application context will not do.
     */
    fun launchPurchase(activity: Activity, productId: String = PRO_PRODUCT_ID) = connected {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val product = details.firstOrNull() ?: return@queryProductDetailsAsync
            // The base plan's offer token is required for a subscription; without it Play
            // rejects the flow with a developer error rather than showing anything.
            val offerToken = product.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
                ?: return@queryProductDetailsAsync

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    private fun connected(block: () -> Unit) {
        if (client.isReady) {
            block()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) block()
                // A failed setup leaves `entitled` null on purpose: the cached value stands.
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun query(type: String, onResult: (List<Purchase>) -> Unit) {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onResult(purchases)
            } else {
                onResult(emptyList())
            }
        }
    }

    private fun apply(purchases: List<Purchase>) {
        val owned = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        // Unacknowledged purchases are refunded automatically after three days. This is the
        // one thing in the billing flow that silently takes money back if it is skipped.
        owned.filterNot { it.isAcknowledged }.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { }
        }

        val isPro = owned.isNotEmpty()
        _entitled.value = isPro
        scope.launch { settings.setProEntitled(isPro) }
    }

    companion object {
        /** Must match the product id created in Play Console. */
        const val PRO_PRODUCT_ID = "snapmind_pro"
    }
}
