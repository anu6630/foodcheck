package com.foodcheck.analytics

interface AnalyticsTracker {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
    fun setUserProperty(name: String, value: String)

    // --- Scanning & Input Selection Events ---
    fun logScanInputSelected(source: String, context: String, slotIndex: Int? = null) {
        logEvent(
            "scan_input_selected",
            mapOf("source" to source, "context" to context, "slot_index" to (slotIndex ?: -1))
        )
    }

    fun logImagePickerResult(source: String, success: Boolean, byteSize: Int? = null) {
        logEvent(
            "image_picker_result",
            mapOf("source" to source, "success" to success, "byte_size" to (byteSize ?: 0))
        )
    }

    fun logScanStarted(source: String, imageCount: Int) {
        logEvent(
            "scan_started",
            mapOf("source" to source, "image_count" to imageCount)
        )
    }

    fun logScanCompleted(flaggedCount: Int, ingredientCount: Int, isCached: Boolean, durationMs: Long) {
        logEvent(
            "scan_completed",
            mapOf(
                "flagged_count" to flaggedCount,
                "ingredient_count" to ingredientCount,
                "is_cached" to isCached,
                "duration_ms" to durationMs
            )
        )
    }

    fun logScanFailed(source: String, error: String, durationMs: Long = 0L) {
        logEvent(
            "scan_failed",
            mapOf("source" to source, "error" to error, "duration_ms" to durationMs)
        )
    }

    fun logScanSuggestionsViewed(candidateCount: Int, flaggedCount: Int) {
        logEvent(
            "scan_suggestions_viewed",
            mapOf("candidate_count" to candidateCount, "flagged_count" to flaggedCount)
        )
    }

    fun logScanAddToCompare(slotIndex: Int) {
        logEvent(
            "scan_add_to_compare",
            mapOf("slot_index" to slotIndex)
        )
    }

    // --- Search Events ---
    fun logSearchPerformed(query: String, resultCount: Int, shelfCount: Int = 0) {
        logEvent(
            "search_query_performed",
            mapOf(
                "query_length" to query.length,
                "result_count" to resultCount,
                "shelf_count" to shelfCount
            )
        )
    }

    fun logSearchResultSelected(resultType: String, itemId: String, itemName: String) {
        logEvent(
            "search_result_selected",
            mapOf("result_type" to resultType, "item_id" to itemId, "item_name" to itemName)
        )
    }

    fun logSearchCleared() {
        logEvent("search_cleared")
    }

    // --- Comparison Events ---
    fun logCompareScreenViewed(slotCount: Int, filledSlots: Int = 0, readySlots: Int = 0) {
        logEvent(
            "compare_screen_viewed",
            mapOf(
                "slot_count" to slotCount,
                "filled_slots" to filledSlots,
                "ready_slots" to readySlots
            )
        )
    }

    fun logCompareItemAdded(slotIndex: Int, source: String, itemName: String) {
        logEvent(
            "compare_item_added",
            mapOf("slot_index" to slotIndex, "source" to source, "item_name" to itemName)
        )
    }

    fun logCompareSlotCleared(slotIndex: Int) {
        logEvent(
            "compare_slot_cleared",
            mapOf("slot_index" to slotIndex)
        )
    }

    fun logCompareEvaluated(readyCount: Int, category: String?) {
        logEvent(
            "compare_evaluated",
            mapOf("ready_count" to readyCount, "category" to (category ?: "unknown"))
        )
    }

    fun logCompareDecision(decision: String, chosenSlot: Int?, addChosenToShelf: Boolean = false) {
        logEvent(
            "compare_decision_made",
            mapOf(
                "decision" to decision,
                "chosen_slot" to (chosenSlot ?: -1),
                "add_to_shelf" to addChosenToShelf
            )
        )
    }

    fun logComparePaywallPrompted() {
        logEvent(
            "compare_paywall_prompted",
            mapOf("feature" to "3_pack_compare")
        )
    }

    fun logCompareRecentOpened(comparisonId: Int) {
        logEvent(
            "compare_recent_opened",
            mapOf("comparison_id" to comparisonId)
        )
    }

    // --- Shelf / Pantry Events ---
    fun logShelfItemSaved(brand: String?, stance: String) {
        logEvent(
            "shelf_item_saved",
            mapOf("brand" to (brand ?: "unknown"), "stance" to stance)
        )
    }

    fun logShelfItemRemoved(shelfItemId: Int) {
        logEvent(
            "shelf_item_removed",
            mapOf("item_id" to shelfItemId)
        )
    }

    fun logShelfItemCompared(shelfItemId: Int) {
        logEvent(
            "shelf_item_compared",
            mapOf("item_id" to shelfItemId)
        )
    }

    // --- Monetization & Conversion Funnel ---
    fun logQuotaLimitReached(used: Int, limit: Int, trigger: String) {
        logEvent(
            "quota_limit_reached",
            mapOf("used" to used, "limit" to limit, "trigger" to trigger)
        )
    }

    fun logQuotaDialogShown(remaining: Int, limit: Int, trigger: String) {
        logEvent(
            "quota_dialog_shown",
            mapOf("remaining" to remaining, "limit" to limit, "trigger" to trigger)
        )
    }

    fun logQuotaDialogUpgradeClicked(trigger: String) {
        logEvent(
            "quota_dialog_upgrade_clicked",
            mapOf("trigger" to trigger)
        )
    }

    fun logQuotaDialogDismissed(trigger: String) {
        logEvent(
            "quota_dialog_dismissed",
            mapOf("trigger" to trigger)
        )
    }

    fun logPaywallViewed(trigger: String, price: String) {
        logEvent(
            "paywall_viewed",
            mapOf("trigger" to trigger, "price" to price)
        )
    }

    fun logPaywallSubscribeClicked(productId: String, price: String, trigger: String) {
        logEvent(
            "paywall_subscribe_clicked",
            mapOf("product_id" to productId, "price" to price, "trigger" to trigger)
        )
    }

    fun logPaywallDismissed(trigger: String, timeSpentSeconds: Long) {
        logEvent(
            "paywall_dismissed",
            mapOf("trigger" to trigger, "time_spent_seconds" to timeSpentSeconds)
        )
    }

    fun logBillingFlowStarted(productId: String) {
        logEvent(
            "billing_flow_started",
            mapOf("product_id" to productId)
        )
    }

    fun logBillingFlowCompleted(productId: String) {
        logEvent(
            "billing_flow_completed",
            mapOf("product_id" to productId)
        )
    }

    fun logBillingFlowCancelled(productId: String, reason: String) {
        logEvent(
            "billing_flow_cancelled",
            mapOf("product_id" to productId, "reason" to reason)
        )
    }

    fun logBillingFlowFailed(productId: String, errorCode: Int, errorMessage: String) {
        logEvent(
            "billing_flow_failed",
            mapOf("product_id" to productId, "error_code" to errorCode, "error_message" to errorMessage)
        )
    }

    fun logSubscriptionSyncStarted(productId: String) {
        logEvent(
            "subscription_sync_started",
            mapOf("product_id" to productId)
        )
    }

    fun logSubscriptionSyncSuccess(productId: String, plan: String, quota: Int) {
        logEvent(
            "subscription_sync_success",
            mapOf("product_id" to productId, "plan" to plan, "quota" to quota)
        )
    }

    fun logSubscriptionSyncFailed(productId: String, error: String) {
        logEvent(
            "subscription_sync_failed",
            mapOf("product_id" to productId, "error" to error)
        )
    }
}

expect fun getAnalyticsTracker(): AnalyticsTracker
