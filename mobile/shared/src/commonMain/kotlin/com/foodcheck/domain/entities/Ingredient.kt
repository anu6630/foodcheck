package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    val id: String,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val inciName: String? = null,
    val casNumber: String? = null,
    val names: List<IngredientName> = emptyList(),
    val bans: List<IngredientBan> = emptyList()
)
