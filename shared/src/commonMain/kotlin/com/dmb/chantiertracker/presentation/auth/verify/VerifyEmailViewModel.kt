package com.dmb.chantiertracker.presentation.auth.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class VerifyEmailUiState(
    val code: String = "",
    val codeError: StringResource? = null,
    val formError: DomainException? = null,
    val codeResent: Boolean = false,
    val isSubmitting: Boolean = false,
    val isResending: Boolean = false,
    val verified: Boolean = false,
)

class VerifyEmailViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state = _state.asStateFlow()

    fun onCodeChange(value: String) {
        _state.update { it.copy(code = value, codeError = null, formError = null, codeResent = false) }
    }

    fun submit(email: String) {
        val current = _state.value
        val codeError = validateCode(current.code)
        if (codeError != null) {
            _state.update { it.copy(codeError = codeError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null, codeResent = false) }
            try {
                authRepository.verifyEmail(email, current.code)
                _state.update { it.copy(isSubmitting = false, verified = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            }
        }
    }

    fun resendCode(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(isResending = true, formError = null, codeResent = false) }
            try {
                authRepository.resendCode(email)
                _state.update { it.copy(isResending = false, codeResent = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isResending = false, formError = e) }
            }
        }
    }
}
