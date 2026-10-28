package com.foodcheck.data.dto

import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.DetectedLabel
import com.foodcheck.domain.entities.Product
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ScanQuota
import kotlinx.serialization.Serializable

@Serializable
data class ScanQuotaDto(
    val used: Int = 0,
    val limit: Int = 5,
    val scanQuota: Int? = null,
    val topupLeft: Int = 0,
    val resetsAt: String = "",
    val plan: String = "free",
    val adsSuppressed: Boolean = false,
) {
    fun toDomain() = ScanQuota(
        used = used,
        limit = scanQuota ?: limit,
        topupLeft = topupLeft,
        resetsAt = resetsAt,
        plan = plan,
        adsSuppressed = adsSuppressed,
    )
}

@Serializable
data class ScanQuotaExceededDto(
    val code: String? = null,
    val detail: String? = null,
    val quota: ScanQuotaDto? = null,
)

/** Raw `/api/scan` response shape. Mapped to the domain [com.foodcheck.domain.entities.ScanOutcome]
 * by [com.foodcheck.data.repositories.ProductRepository] — kept separate from the domain model so a
 * backend response-shape change doesn't ripple into use cases/ViewModels. */
@Serializable
data class ScanCandidateDto(
    val product: Product,
    val matchScore: Int = 0
)

@Serializable
data class ScanResponse(
    val matchType: String, // "confident" | "suggestions" | "ingredients_only"
    val product: SearchProductResult? = null,
    val candidates: List<ScanCandidateDto> = emptyList(),
    val ingredients: List<AnnotatedIngredient> = emptyList(),
    val detected: DetectedLabel? = null,
    val quota: ScanQuotaDto? = null,
)
