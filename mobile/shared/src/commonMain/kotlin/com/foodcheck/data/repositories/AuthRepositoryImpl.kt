package com.foodcheck.data.repositories

import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.datasources.ShelfLocalStore
import com.foodcheck.data.network.BackendConfig
import com.foodcheck.domain.entities.AuthLinkRequest
import com.foodcheck.domain.entities.AuthLinkResponse
import com.foodcheck.domain.entities.UserProfile
import com.foodcheck.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(
    private val network: NetworkDataSource,
    private val shelfLocalStore: ShelfLocalStore? = null,
) : AuthRepository {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    override val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    override suspend fun loadProfile(): Result<UserProfile> = runCatching {
        val p = network.getProfile()
        _profile.value = p
        p
    }

    override suspend fun linkFirebase(
        firebaseUid: String,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ): Result<AuthLinkResponse> = runCatching {
        val res = network.linkAccount(
            AuthLinkRequest(
                firebaseUid = firebaseUid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl
            )
        )
        _profile.value = res.user
        res
    }

    override suspend fun signOut() {
        // 1. Tell backend to unlink this device session from the user account
        runCatching { network.signOut() }
        // 2. Clear local shelf cache so fresh guest session starts clean
        runCatching { shelfLocalStore?.clear() }
        // 3. Reset device key to start an anonymous guest session
        BackendConfig.deviceKey = ""
        BackendConfig.ensureDeviceKey()
        _profile.value = null
        loadProfile()
    }
}
