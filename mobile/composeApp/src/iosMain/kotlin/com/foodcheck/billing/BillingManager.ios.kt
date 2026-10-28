package com.foodcheck.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosBillingManager : BillingManager {
    private val _proPlanDetails = MutableStateFlow<SubscriptionPlanDetails?>(
        SubscriptionPlanDetails(
            productId = "pro_monthly",
            title = "FoodCheck Pro",
            formattedPrice = "₹99/mo",
            description = "50 AI scans per month, no ads, unlimited packs"
        )
    )
    override val proPlanDetails: StateFlow<SubscriptionPlanDetails?> = _proPlanDetails.asStateFlow()

    private val _purchaseState = MutableStateFlow<BillingPurchaseState>(BillingPurchaseState.Idle)
    override val purchaseState: StateFlow<BillingPurchaseState> = _purchaseState.asStateFlow()

    override fun startBillingConnection() {}

    override fun launchSubscriptionFlow(productId: String) {
        _purchaseState.value = BillingPurchaseState.Error("In-app billing not configured for iOS yet")
    }

    override fun resetPurchaseState() {
        _purchaseState.value = BillingPurchaseState.Idle
    }
}

private val iosBilling = IosBillingManager()

actual fun getBillingManager(): BillingManager = iosBilling
