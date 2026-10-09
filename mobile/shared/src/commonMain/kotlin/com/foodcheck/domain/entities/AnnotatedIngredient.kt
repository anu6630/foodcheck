package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class AnnotatedIngredient(
    val id: String? = null,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val codes: List<IngredientCode> = emptyList(),
    val severity: String, // "red", "amber", "green", "gray"
    val matchedBans: List<IngredientBan> = emptyList(),
    val order: Int = 0
) {

    fun codesText(): String? {
        if (codes.isNotEmpty()) {
            val label = codes.joinToString(" · ") { it.display.ifBlank { "${it.system} ${it.code}" } }
            if (label.isNotBlank()) return label
        }
        return eNumber
    }
}
