package com.foodcheck.domain.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthFlag(
    val kind: String,
    val label: String,
    val value: String? = null,
)

@Serializable
data class AnnotatedIngredient(
    val id: String? = null,
    val name: String,
    val category: String? = null,
    val eNumber: String? = null,
    val codes: List<IngredientCode> = emptyList(),
    /** Regulation severity for [marketCode] — not a safety score. */
    val severity: String, // "red", "amber", "green", "gray"
    val matchedBans: List<IngredientBan> = emptyList(),
    @SerialName("market_code")
    val marketCode: String = "IN",
    @SerialName("has_health_watch")
    val hasHealthWatch: Boolean = false,
    @SerialName("health_flags")
    val healthFlags: List<HealthFlag> = emptyList(),
    val order: Int = 0,
    /** "group" = expandable role chip (Colours / Emulsifiers); default item. */
    val kind: String = "item",
    val role: String? = null,
    val labelText: String? = null,
    val members: List<AnnotatedIngredient> = emptyList(),
) {
    val isGroup: Boolean get() = kind.equals("group", ignoreCase = true) && members.isNotEmpty()

    /** True if this item or any group member has health signals. */
    val showsWatch: Boolean
        get() = hasHealthWatch || healthFlags.isNotEmpty() || members.any { it.showsWatch }

    fun regulationLabel(): String {
        val market = marketCode.ifBlank { "IN" }
        return when (severity.lowercase()) {
            "red" -> "Banned ($market)"
            "amber" -> "Restricted ($market)"
            "green", "permitted" -> "Permitted ($market)"
            else -> "No data"
        }
    }

    fun codesText(): String? {
        if (codes.isNotEmpty()) {
            val label = codes.joinToString(" · ") { it.display.ifBlank { "${it.system} ${it.code}" } }
            if (label.isNotBlank()) return label
        }
        return eNumber
    }

    /** Substances to persist on shelf/story — expand groups so bans stay attachable. */
    fun forSnapshot(): List<AnnotatedIngredient> =
        if (isGroup) members.flatMap { it.forSnapshot() } else listOf(this)

    fun memberCodePreview(limit: Int = 8): String {
        if (!isGroup) return ""
        val bits = members.map { m ->
            m.codes.firstOrNull()?.code
                ?: m.eNumber?.removePrefix("E")?.removePrefix("e")
                ?: m.name.removePrefix("INS ").take(12)
        }
        val shown = bits.take(limit)
        return shown.joinToString(" · ") + if (bits.size > limit) " …" else ""
    }
}

/** Counts for the label summary strip. Groups expand to members for regulation; watch uses chip-level. */
fun labelRegulationSummary(ingredients: List<AnnotatedIngredient>): LabelSummary {
    fun flat(items: List<AnnotatedIngredient>): List<AnnotatedIngredient> =
        items.flatMap { if (it.isGroup) it.members.ifEmpty { listOf(it) } else listOf(it) }

    val flat = flat(ingredients)
    return LabelSummary(
        banned = flat.count { it.severity.equals("red", true) },
        restricted = flat.count { it.severity.equals("amber", true) },
        permitted = flat.count {
            it.severity.equals("green", true) || it.severity.equals("permitted", true)
        },
        unknown = flat.count {
            val s = it.severity.lowercase()
            s != "red" && s != "amber" && s != "green" && s != "permitted"
        },
        watch = ingredients.count { it.showsWatch },
        marketCode = ingredients.firstOrNull()?.marketCode?.ifBlank { "IN" } ?: "IN",
    )
}

data class LabelSummary(
    val banned: Int,
    val restricted: Int,
    val permitted: Int,
    val unknown: Int,
    val watch: Int,
    val marketCode: String,
) {
    val substanceCount: Int get() = banned + restricted + permitted + unknown
}
