package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Int? = null,
    val name: String,
    val brand: String? = null,
    val barcode: String,
    val category: String? = null,
    val source: String,
    val ingredients: List<ProductIngredient> = emptyList()
)
