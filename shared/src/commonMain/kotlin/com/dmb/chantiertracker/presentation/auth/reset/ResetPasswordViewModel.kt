package com.dmb.chantiertracker.presentation.auth.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateCode
import com.dmb.chantiertracker.presentation.auth.validateStrongPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class ResetPasswordUiState(
    val code: String = "",
    val newPassword: String = "",
    val codeError: StringResource? = null,
    val passwordError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val reset: Boolean = false,
)

class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ResetPasswordUiState())
    val state = _state.asStateFlow()

    fun onCodeChange(value: String) = _state.update { it.copy(code = value, codeError = null, formError = null) }
    fun onPasswordChange(value: String) = _state.update {
        it.copy(
            newPassword = value,
            passwordError = if (value.isEmpty()) null else validateStrongPassword(value),
            formError = null,
        )
    }

    fun submit(email: String) {
        val current = _state.value
        val codeError = validateCode(current.code)
        val passwordError = validateStrongPassword(current.newPassword)
        if (codeError != null || passwordError != null) {
            _state.update { it.copy(codeError = codeError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                authRepository.resetPassword(email, current.code, current.newPassword)
                _state.update { it.copy(isSubmitting = false, reset = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            }
        }
    }
}
