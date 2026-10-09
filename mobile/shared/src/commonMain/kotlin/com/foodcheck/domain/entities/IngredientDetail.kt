package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

/**
 * Standalone ingredient page data. Unlike [AnnotatedIngredient] (which only carries bans relevant
 * to a severity badge), this carries every country's status, [useLimits] (per-food caps), and
 * [trackedCountries]. Absence of a ban row is unknown — not verified-safe.
 */
@Serializable
data class IngredientCode(
    val system: String,
    val code: String,
    val display: String = ""
)

@Serializable
data class IngredientUseLimit(
    val countryCode: String,
    val foodCategoryCode: String = "",
    val foodCategoryName: String? = null,
    val maxLevel: String = "",
    val note: String? = null,
    val regulationRef: String? = null,
    val countryLabel: String? = null
)

@Serializable
data class DataSourceFile(
    val id: Int,
    val sourceId: String = "",
    val kind: String = "original",
    val filename: String,
    val contentType: String? = null,
    val bytes: Long? = null,
    val sha256: String,
    val collectedFromUrl: String? = null,
    val archivedAt: String = "",
    val downloadUrl: String? = null
)

@Serializable
data class DataSource(
    val id: String,
    val publisher: String,
    val countryCode: String? = null,
    val title: String,
    val version: String? = null,
    val versionDate: String? = null,
    val citation: String? = null,
    val citationUrl: String? = null,
    val url: String? = null,
    val urls: List<String> = emptyList(),
    val filePath: String? = null,
    val fileSha256: String? = null,
    val fileBytes: Long? = null,
    val files: List<DataSourceFile> = emptyList(),
    val retrievedAt: String = "",
    val ingestedAt: String = "",
    val notes: String? = null
)

@Serializable
data class IngredientDetail(
    val id: String,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val inciName: String? = null,
    val casNumber: String? = null,
    val codes: List<IngredientCode> = emptyList(),
    val codesLabel: String? = null,
    val names: List<IngredientName> = emptyList(),
    val bans: List<IngredientBan> = emptyList(),
    val useLimits: List<IngredientUseLimit> = emptyList(),
    val useLimitCounts: Map<String, Int> = emptyMap(),
    val sources: List<DataSource> = emptyList(),
    val trackedCountries: List<String> = emptyList()
)
