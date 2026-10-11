package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

/**
 * What the scan agent read from the photos themselves, independent of any catalog match.
 * [imageRoles] has one entry per uploaded photo in upload order ("front", "ingredients",
 * "both", "other"), or is empty when the model could not say.
 */
@Serializable
data class DetectedLabel(
    val productName: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val imageRoles: List<String> = emptyList(),
    val ingredientsComplete: Boolean? = null,
) {
    fun roleOf(index: Int): String? = imageRoles.getOrNull(index)

    /** True when roles are known and no photo shows an ingredients list. */
    val frontOnly: Boolean
        get() = imageRoles.isNotEmpty() && imageRoles.none { it == "ingredients" || it == "both" }
}

data class AssignedPhotos<T>(val front: T?, val label: T?)

/**
 * Which uploaded photo is the front and which is the ingredients label, from [DetectedLabel.imageRoles].
 * A "both" photo is the label (and the thumbnail). Without roles, falls back to upload order:
 * a single photo is the label; with two, the first is the front and the last the label.
 */
fun <T> DetectedLabel.assignPhotos(photos: List<T>): AssignedPhotos<T> {
    if (photos.isEmpty()) return AssignedPhotos(null, null)
    if (imageRoles.size != photos.size) {
        return AssignedPhotos(front = photos.first().takeIf { photos.size > 1 }, label = photos.last())
    }
    val label = photos.indices.firstOrNull { imageRoles[it] == "ingredients" || imageRoles[it] == "both" }
    val front = photos.indices.firstOrNull { imageRoles[it] == "front" }
    return AssignedPhotos(front = front?.let(photos::get), label = label?.let(photos::get))
}
