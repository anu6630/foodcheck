package com.foodcheck.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class IosGoogleAuthClient : GoogleAuthClient {
    override suspend fun signIn(): Result<GoogleAuthUser> {
        return Result.failure(UnsupportedOperationException("Google Sign-In on iOS is not yet configured"))
    }
}

@Composable
actual fun rememberGoogleAuthClient(): GoogleAuthClient {
    return remember { IosGoogleAuthClient() }
}
