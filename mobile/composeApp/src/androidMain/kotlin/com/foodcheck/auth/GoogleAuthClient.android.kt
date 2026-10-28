package com.foodcheck.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

private const val TAG = "FoodCheck.GoogleAuth"
private const val WEB_CLIENT_ID = "36255520715-oap582rggip95r6fv62m46k2nbo1jk1m.apps.googleusercontent.com"

class AndroidGoogleAuthClient(
    private val context: Context
) : GoogleAuthClient {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun signIn(): Result<GoogleAuthUser> {
        val activity = context as? Activity
            ?: return Result.failure(IllegalStateException("Context is not an Activity"))

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()

                val auth = FirebaseAuth.getInstance()
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val firebaseUser = authResult.user
                    ?: return Result.failure(IllegalStateException("Firebase user is null after sign in"))

                Result.success(
                    GoogleAuthUser(
                        firebaseUid = firebaseUser.uid,
                        email = firebaseUser.email ?: email,
                        displayName = firebaseUser.displayName ?: displayName,
                        photoUrl = firebaseUser.photoUrl?.toString() ?: photoUrl
                    )
                )
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential::class.simpleName}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google sign-in cancelled by user")
            Result.failure(GoogleAuthCancelledException())
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in credential error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in error: ${e.message}", e)
            Result.failure(e)
        }
    }
}

@Composable
actual fun rememberGoogleAuthClient(): GoogleAuthClient {
    val context = LocalContext.current
    return remember(context) { AndroidGoogleAuthClient(context) }
}
