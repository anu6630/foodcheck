package com.foodcheck.data.dto

import com.foodcheck.domain.entities.AnnotatedIngredient
import kotlinx.serialization.Serializable

@Serializable
data class CommunityIdResponse(val id: Int, val disclaimer: String? = null)

@Serializable
data class ShelfListResponse(val items: List<ShelfItemDto> = emptyList())

@Serializable
data class ShelfItemDto(
    val id: Int,
    val displayName: String,
    val brand: String? = null,
    val barcode: String? = null,
    val createdAt: String? = null,
    val hasPhoto: Boolean = false,
    val ingredients: List<AnnotatedIngredient> = emptyList(),
    val disclaimer: String? = null
)

@Serializable
data class StoryListResponse(val stories: List<StoryDto> = emptyList())

@Serializable
data class StoryDto(
    val id: Int,
    val caption: String? = null,
    val publishedAt: String? = null,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val hasPhoto: Boolean = false,
    val ingredients: List<AnnotatedIngredient> = emptyList(),
    val comments: List<StoryCommentDto> = emptyList(),
    val disclaimer: String? = null
)

@Serializable
data class StoryCommentDto(
    val id: Int = 0,
    val body: String,
    val createdAt: String? = null
)

@Serializable
data class LikeResponse(val liked: Boolean = false)

@Serializable
data class CommentsResponse(val comments: List<StoryCommentDto> = emptyList())
