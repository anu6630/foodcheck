package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class IngredientName(
    val id: Int? = null,
    val ingredientId: String,
    val lang: String,
    val name: String
)

@Serializable
data class IngredientBan(
    val countryCode: String,
    val status: String,
    val condition: String? = null,
    val regulationRef: String? = null,
    val effectiveDate: String? = null
)

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

@Serializable
data class ProductIngredient(
    val ingredient: Ingredient,
    val order: Int
)

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

@Serializable
data class AnnotatedIngredient(
    val id: String? = null,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val severity: String, // "red", "amber", "green", "gray"
    val matchedBan: IngredientBan? = null,
    val order: Int = 0
)

@Serializable
data class SearchProductResult(
    val product: Product,
    val severity: String,
    val ingredients: List<AnnotatedIngredient> = emptyList()
)

@Serializable
data class ScanResult(
    val intent: String, // "product" or "ingredients"
    val data: SearchProductResult? = null, // if intent is product
    val match_score: Int? = null,           // if intent is product
    val ingredients: List<AnnotatedIngredient> = emptyList(), // if intent is ingredients
    val matching_products: List<SearchProductResult> = emptyList() // if intent is ingredients
)
