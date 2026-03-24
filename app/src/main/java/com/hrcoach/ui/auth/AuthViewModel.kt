package com.hrcoach.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    val isAuthenticated: Boolean
        get() = authRepository.isAuthenticated()

    val currentUser = authRepository.currentUser

    fun setEmail(email: String) = _state.update { it.copy(email = email, error = null) }
    fun setPassword(password: String) = _state.update { it.copy(password = password, error = null) }
    fun toggleSignUp() = _state.update { it.copy(isSignUp = !it.isSignUp, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Email and password required") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.isSignUp) {
                authRepository.signUpWithEmail(s.email.trim(), s.password)
            } else {
                authRepository.signInWithEmail(s.email.trim(), s.password)
            }
            result.fold(
                onSuccess = { _state.update { it.copy(isLoading = false) } },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Authentication failed") }
                }
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.signInWithGoogleCredential(idToken).fold(
                onSuccess = { _state.update { it.copy(isLoading = false) } },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Google sign-in failed") }
                }
            )
        }
    }
}
