package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class IngredientName(
    val id: Int? = null,
    val lang: String,
    val name: String
)
