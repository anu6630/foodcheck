package com.foodcheck.domain.entities

enum class ShelfStance(val apiValue: String, val label: String) {
    Avoid("avoid", "Avoid"),
    Occasional("occasional", "Occasional"),
    Ok("ok", "OK"),
    Unsure("unsure", "Unsure");

    companion object {
        fun fromApi(raw: String?): ShelfStance {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.apiValue == key } ?: Unsure
        }
    }
}

val SHELF_TAG_OPTIONS = listOf("Kids", "Adults", "Elderly", "Home")
