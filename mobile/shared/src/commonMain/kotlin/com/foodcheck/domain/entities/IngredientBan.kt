package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class IngredientBan(
    val countryCode: String,
    val status: String,
    val condition: String? = null,
    val regulationRef: String? = null,
    val effectiveDate: String? = null
)
