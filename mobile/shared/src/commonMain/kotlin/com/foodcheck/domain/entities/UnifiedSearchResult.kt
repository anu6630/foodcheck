package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

/** Grouped (not interleaved) search results: a query can match product titles and/or ingredient names/synonyms. */
@Serializable
data class UnifiedSearchResult(
    val products: List<SearchProductResult> = emptyList(),
    val ingredients: List<AnnotatedIngredient> = emptyList()
)
