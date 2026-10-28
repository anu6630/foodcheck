package com.foodcheck.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val isAnonymous: Boolean = true,
    val firebaseUid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val phone: String? = null,
    val deviceCount: Int = 1,
    val shelfCount: Int = 0,
)

@Serializable
data class AuthLinkResponse(
    val status: String = "linked",
    val userId: String = "",
    val mergedShelfItems: Int = 0,
    val mergedScans: Int = 0,
    val user: UserProfile,
)

@Serializable
data class AuthLinkRequest(
    val firebaseUid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val phone: String? = null,
)
