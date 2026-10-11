package com.foodcheck.data.dto

import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.DetectedLabel
import com.foodcheck.domain.entities.Product
import com.foodcheck.domain.entities.SearchProductResult
import kotlinx.serialization.Serializable

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
)
