package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class SearchProductResult(
    val product: Product,
    val severity: String,
    val ingredients: List<AnnotatedIngredient> = emptyList()
)
