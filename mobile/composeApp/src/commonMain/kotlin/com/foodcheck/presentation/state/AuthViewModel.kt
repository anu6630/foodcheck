package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.domain.entities.UserProfile
import com.foodcheck.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val error: String? = null,
    val successMessage: String? = null,
)

class AuthViewModel(
    private val authRepo: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authRepo.loadProfile()
                .onSuccess { profile ->
                    _state.value = _state.value.copy(isLoading = false, profile = profile)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(isLoading = false, error = err.message)
                }
        }
    }

    fun linkAccount(firebaseUid: String, email: String?, displayName: String?, photoUrl: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, successMessage = null)
            authRepo.linkFirebase(firebaseUid, email, displayName, photoUrl)
                .onSuccess { res ->
                    val msg = if (res.status == "merged") {
                        "Account synced! Merged ${res.mergedShelfItems} packs & ${res.mergedScans} scans into your account."
                    } else {
                        "Account connected! Your packs are now backed up to ${res.user.email ?: "your account"}."
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        profile = res.user,
                        successMessage = msg
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = err.message ?: "Failed to link account"
                    )
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
            loadProfile()
        }
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(isLoading = false, error = message)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }
}
