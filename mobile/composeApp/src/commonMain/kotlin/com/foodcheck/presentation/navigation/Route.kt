package com.foodcheck.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Landing : Route
    @Serializable data class SearchResults(val initialQuery: String = "") : Route

    /** Parent graph for the scan sub-flow, so Capture and Suggestions share one ScanViewModel. */
    @Serializable data object ScanGraph : Route
    @Serializable data object ScanCapture : Route
    @Serializable data object ScanSuggestions : Route

    @Serializable data class ProductDetail(val barcode: String) : Route
    @Serializable data class IngredientDetail(val ingredientId: String) : Route
    @Serializable data object SubmitRequest : Route
}
