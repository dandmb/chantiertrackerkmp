package com.dmb.chantiertracker.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateEmail
import com.dmb.chantiertracker.presentation.auth.validateRequiredPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: StringResource? = null,
    val passwordError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val needsVerification: Boolean = false,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun prefillEmail(email: String) {
        if (_state.value.email.isEmpty()) {
            _state.update { it.copy(email = email) }
        }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailError = null, formError = null, needsVerification = false) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun submit() {
        val current = _state.value
        val emailError = validateEmail(current.email)
        val passwordError = validateRequiredPassword(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null, needsVerification = false) }
            try {
                authRepository.login(current.email.trim(), current.password)
            } catch (e: DomainException) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        formError = e,
                        needsVerification = e is DomainException.AccountNotVerified,
                    )
                }
            }
        }
    }

    fun resendVerificationCode() {
        val email = _state.value.email.trim()
        if (validateEmail(email) != null) return
        viewModelScope.launch {
            runCatching { authRepository.resendCode(email) }
        }
    }
}
