package com.foodcheck.domain.repositories

import com.foodcheck.domain.entities.AuthLinkResponse
import com.foodcheck.domain.entities.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val profile: StateFlow<UserProfile?>
    suspend fun loadProfile(): Result<UserProfile>
    suspend fun linkFirebase(
        firebaseUid: String,
        email: String? = null,
        displayName: String? = null,
        photoUrl: String? = null
    ): Result<AuthLinkResponse>
    suspend fun signOut()
}
