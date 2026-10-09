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
    data class Confident(
        val product: SearchProductResult,
        val ingredients: List<AnnotatedIngredient> = emptyList()
    ) : ScanOutcome()
    data class Suggestions(
        val candidates: List<ScanCandidate>,
        val ingredients: List<AnnotatedIngredient> = emptyList()
    ) : ScanOutcome()
    data class IngredientsOnly(val ingredients: List<AnnotatedIngredient>) : ScanOutcome()
}
