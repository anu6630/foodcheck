package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

/**
 * Standalone ingredient page data. Unlike [AnnotatedIngredient] (which only carries bans relevant
 * to a severity badge), this carries *every* country's status plus [trackedCountries] so the UI
 * can say "not flagged in the N countries we track" instead of implying a verified-safe result —
 * ban data only exists for a small subset of the ingredient catalog today.
 */
@Serializable
data class IngredientDetail(
    val id: String,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val inciName: String? = null,
    val casNumber: String? = null,
    val names: List<IngredientName> = emptyList(),
    val bans: List<IngredientBan> = emptyList(),
    val trackedCountries: List<String> = emptyList()
)
