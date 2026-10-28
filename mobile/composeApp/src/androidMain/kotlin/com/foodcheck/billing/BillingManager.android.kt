package com.foodcheck.billing

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.foodcheck.ads.AppOpenAdManagerActivityHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidBillingManager(
    private val context: Context
) : BillingManager, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _proPlanDetails = MutableStateFlow<SubscriptionPlanDetails?>(null)
    override val proPlanDetails: StateFlow<SubscriptionPlanDetails?> = _proPlanDetails.asStateFlow()

    private val _purchaseState = MutableStateFlow<BillingPurchaseState>(BillingPurchaseState.Idle)
    override val purchaseState: StateFlow<BillingPurchaseState> = _purchaseState.asStateFlow()

    private var cachedProductDetails: ProductDetails? = null

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()
    }

    override fun startBillingConnection() {
        if (billingClient.isReady) {
            queryProducts()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryActivePurchases()
                } else {
                    Log.w("BillingManager", "Billing setup failed: ${billingResult.debugMessage}")
                    // Provide default display info so UI shows pricing immediately
                    _proPlanDetails.value = SubscriptionPlanDetails(
                        productId = "pro_monthly",
                        title = "FoodCheck Pro",
                        formattedPrice = "₹99/month",
                        description = "50 AI label scans / month, no ads, unlimited packs"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("BillingManager", "Billing service disconnected")
            }
        })
    }

    private fun queryProducts() {
        scope.launch {
            try {
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("pro_monthly")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )

                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                val result = billingClient.queryProductDetails(params)
                val productDetails = result.productDetailsList?.firstOrNull()

                if (productDetails != null) {
                    cachedProductDetails = productDetails
                    val offer = productDetails.subscriptionOfferDetails?.firstOrNull()
                    val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "₹99/month"

                    _proPlanDetails.value = SubscriptionPlanDetails(
                        productId = productDetails.productId,
                        title = productDetails.name,
                        formattedPrice = price,
                        description = productDetails.description
                    )
                } else {
                    _proPlanDetails.value = SubscriptionPlanDetails(
                        productId = "pro_monthly",
                        title = "FoodCheck Pro",
                        formattedPrice = "₹99/month",
                        description = "50 AI label scans / month, no ads, unlimited packs"
                    )
                }
            } catch (e: Exception) {
                Log.w("BillingManager", "Query products failed", e)
                _proPlanDetails.value = SubscriptionPlanDetails(
                    productId = "pro_monthly",
                    title = "FoodCheck Pro",
                    formattedPrice = "₹99/month",
                    description = "50 AI label scans / month, no ads, unlimited packs"
                )
            }
        }
    }

    private fun queryActivePurchases() {
        scope.launch {
            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                val result = billingClient.queryPurchasesAsync(params)
                val purchase = result.purchasesList.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchase != null) {
                    _purchaseState.value = BillingPurchaseState.Success(
                        purchaseToken = purchase.purchaseToken,
                        productId = purchase.products.firstOrNull() ?: "pro_monthly"
                    )
                }
            } catch (e: Exception) {
                Log.w("BillingManager", "Query active purchases failed", e)
            }
        }
    }

    override fun launchSubscriptionFlow(productId: String) {
        val activity = AppOpenAdManagerActivityHolder.current
        if (activity == null) {
            _purchaseState.value = BillingPurchaseState.Error("Activity context not available")
            return
        }

        val details = cachedProductDetails
        if (details == null) {
            _purchaseState.value = BillingPurchaseState.Error("Plan not yet available in this Play Store region")
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            _purchaseState.value = BillingPurchaseState.Error("No active offer found for this product")
            return
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        _purchaseState.value = BillingPurchaseState.InProgress
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchaseIfNecessary(purchase)
                    _purchaseState.value = BillingPurchaseState.Success(
                        purchaseToken = purchase.purchaseToken,
                        productId = purchase.products.firstOrNull() ?: "pro_monthly"
                    )
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseState.value = BillingPurchaseState.Idle
        } else {
            _purchaseState.value = BillingPurchaseState.Error(
                billingResult.debugMessage.ifBlank { "Purchase failed with code ${billingResult.responseCode}" }
            )
        }
    }

    private fun acknowledgePurchaseIfNecessary(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            scope.launch {
                try {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(params)
                } catch (e: Exception) {
                    Log.w("BillingManager", "Failed to acknowledge purchase", e)
                }
            }
        }
    }

    override fun resetPurchaseState() {
        _purchaseState.value = BillingPurchaseState.Idle
    }
}

private var instance: AndroidBillingManager? = null

fun initAndroidBillingManager(context: Context): BillingManager {
    if (instance == null) {
        instance = AndroidBillingManager(context.applicationContext)
    }
    return instance!!
}

actual fun getBillingManager(): BillingManager {
    return instance ?: error("BillingManager not initialized. Call initAndroidBillingManager(context) in MainActivity.")
}
