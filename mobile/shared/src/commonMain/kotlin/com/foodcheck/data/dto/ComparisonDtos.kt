package com.foodcheck.data.dto

import com.foodcheck.domain.entities.AnnotatedIngredient
import kotlinx.serialization.Serializable

@Serializable
data class ComparisonListResponse(val comparisons: List<ComparisonDto> = emptyList())

/** List rows carry stored counts only; [ComparisonItemDto.ingredients] is filled on detail. */
@Serializable
data class ComparisonDto(
    val id: Int,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    /** "picked" | "none" | "undecided" */
    val decision: String = "undecided",
    val chosenSlot: Int? = null,
    val reasons: List<String> = emptyList(),
    val note: String? = null,
    val createdAt: String? = null,
    val items: List<ComparisonItemDto> = emptyList(),
    val disclaimer: String? = null,
)

@Serializable
data class ComparisonItemDto(
    val slot: Int,
    val scanId: Int,
    val displayName: String,
    val brand: String? = null,
    val hasFront: Boolean = false,
    val hasLabel: Boolean = false,
    val labelDate: String? = null,
    val flaggedCount: Int = 0,
    val ingredientCount: Int = 0,
    val ingredients: List<AnnotatedIngredient> = emptyList(),
)

/** One product in a save request. Either [scanId] (reused label) or [snapshot] (new read, label order). */
data class ComparisonItemDraft(
    val slot: Int,
    val displayName: String,
    val brand: String?,
    val scanId: Int?,
    val snapshot: List<AnnotatedIngredient>,
    val labelImage: ByteArray?,
    val frontImage: ByteArray?,
)

data class ComparisonDraft(
    val category: String?,
    val tags: List<String>,
    val decision: String,
    val chosenSlot: Int?,
    val reasons: List<String>,
    val note: String?,
    val addChosenToShelf: Boolean,
    val items: List<ComparisonItemDraft>,
)
