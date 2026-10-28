package com.foodcheck.domain.entities

/** A ranked, unconfirmed candidate product returned by the scan agent for user disambiguation. */
data class ScanCandidate(
    val product: Product,
    val matchScore: Int
)

/**
 * Domain-level result of a scan, mapped from the wire [com.foodcheck.data.dto.ScanResponse].
 * [Confident] means the agent found one clear match (no disambiguation needed, navigate straight
 * to Product Detail); [Suggestions] means multiple close candidates need user disambiguation;
 * [IngredientsOnly] means no product could be matched but ingredients were still extracted.
 */
sealed class ScanOutcome {
    /** Name/brand/category/photo roles read from the photos — present for every outcome. */
    abstract val detected: DetectedLabel
    open val quota: ScanQuota? get() = null

    data class Confident(
        val product: SearchProductResult,
        val ingredients: List<AnnotatedIngredient> = emptyList(),
        override val detected: DetectedLabel = DetectedLabel(),
        override val quota: ScanQuota? = null,
    ) : ScanOutcome()
    data class Suggestions(
        val candidates: List<ScanCandidate>,
        val ingredients: List<AnnotatedIngredient> = emptyList(),
        override val detected: DetectedLabel = DetectedLabel(),
        override val quota: ScanQuota? = null,
    ) : ScanOutcome()
    data class IngredientsOnly(
        val ingredients: List<AnnotatedIngredient>,
        override val detected: DetectedLabel = DetectedLabel(),
        override val quota: ScanQuota? = null,
    ) : ScanOutcome()

    /** Best product name: what the pack says, else the confident catalog match. */
    fun suggestedName(): String? =
        detected.productName?.takeIf { it.isNotBlank() }
            ?: (this as? Confident)?.product?.product?.name?.takeIf { it.isNotBlank() }

    fun suggestedBrand(): String? =
        detected.brand?.takeIf { it.isNotBlank() }
            ?: (this as? Confident)?.product?.product?.brand?.takeIf { it.isNotBlank() }

    fun labelIngredients(): List<AnnotatedIngredient> = when (this) {
        is Confident -> ingredients
        is Suggestions -> ingredients
        is IngredientsOnly -> ingredients
    }
}
