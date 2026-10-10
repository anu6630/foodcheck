package com.foodcheck.data.dto

import com.foodcheck.domain.entities.AnnotatedIngredient
import kotlinx.serialization.Serializable

@Serializable
data class CommunityIdResponse(val id: Int, val disclaimer: String? = null)

@Serializable
data class ShelfListResponse(val items: List<ShelfItemDto> = emptyList())

@Serializable
data class CatalogCompanyDto(
    val id: Int,
    val name: String,
)

@Serializable
data class CatalogBrandDto(
    val id: Int,
    val name: String,
    val companyId: Int? = null,
    val companyName: String? = null,
)

@Serializable
data class BrandListResponse(val brands: List<CatalogBrandDto> = emptyList())

@Serializable
data class CompanyListResponse(val companies: List<CatalogCompanyDto> = emptyList())

@Serializable
data class ShelfItemDto(
    val id: Int,
    val displayName: String,
    val brand: String? = null,
    val brandId: Int? = null,
    val barcode: String? = null,
    val createdAt: String? = null,
    val hasPhoto: Boolean = false,
    val companies: List<CatalogCompanyDto> = emptyList(),
    val ingredients: List<AnnotatedIngredient> = emptyList(),
    val disclaimer: String? = null
)

@Serializable
data class StoryListResponse(
    val stories: List<StoryDto> = emptyList(),
    val hasMore: Boolean = false,
)

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
