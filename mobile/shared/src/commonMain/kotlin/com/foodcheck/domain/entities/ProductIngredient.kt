package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class ProductIngredient(
    val ingredient: Ingredient,
    val order: Int
)
