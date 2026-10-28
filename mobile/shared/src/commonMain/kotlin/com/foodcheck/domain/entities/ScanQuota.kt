package com.foodcheck.domain.entities

data class ScanQuota(
    val used: Int = 0,
    val limit: Int = 5,
    val topupLeft: Int = 0,
    val resetsAt: String = "",
    val plan: String = "free",
    val adsSuppressed: Boolean = false,
) {
    val remaining: Int get() = (limit - used + topupLeft).coerceAtLeast(0)
    val isExceeded: Boolean get() = remaining <= 0
    val isPro: Boolean get() = plan == "pro" || plan == "family"
}

class ScanQuotaExceededException(
    val quota: ScanQuota,
    message: String = "Monthly AI scan limit reached (${quota.used}/${quota.limit})"
) : Exception(message)
