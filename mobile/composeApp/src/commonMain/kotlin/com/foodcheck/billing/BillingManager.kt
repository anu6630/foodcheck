package com.foodcheck.billing

import kotlinx.coroutines.flow.StateFlow

data class SubscriptionPlanDetails(
    val productId: String,
    val title: String,
    val formattedPrice: String,
    val description: String
)

sealed interface BillingPurchaseState {
    data object Idle : BillingPurchaseState
    data object InProgress : BillingPurchaseState
    data class Success(val purchaseToken: String, val productId: String) : BillingPurchaseState
    data class Error(val message: String) : BillingPurchaseState
}

interface BillingManager {
    val proPlanDetails: StateFlow<SubscriptionPlanDetails?>
    val purchaseState: StateFlow<BillingPurchaseState>

    fun startBillingConnection()
    fun launchSubscriptionFlow(productId: String = "pro_monthly")
    fun resetPurchaseState()
}

expect fun getBillingManager(): BillingManager
