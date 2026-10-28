package com.foodcheck.auth

import androidx.compose.runtime.Composable

data class GoogleAuthUser(
    val firebaseUid: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)

class GoogleAuthCancelledException : Exception("Google sign-in cancelled")

interface GoogleAuthClient {
    suspend fun signIn(): Result<GoogleAuthUser>
}

@Composable
expect fun rememberGoogleAuthClient(): GoogleAuthClient
