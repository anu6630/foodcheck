package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class AnnotatedIngredient(
    val id: String? = null,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val severity: String, // "red", "amber", "green", "gray"
    val matchedBans: List<IngredientBan> = emptyList(),
    val order: Int = 0
)
