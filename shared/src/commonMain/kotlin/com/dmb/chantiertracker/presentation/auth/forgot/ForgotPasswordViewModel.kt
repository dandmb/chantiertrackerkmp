package com.dmb.chantiertracker.presentation.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val submittedEmail: String? = null,
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun submit() {
        val current = _state.value
        val emailError = validateEmail(current.email)
        if (emailError != null) {
            _state.update { it.copy(emailError = emailError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                val email = current.email.trim()
                authRepository.forgotPassword(email)
                _state.update { it.copy(isSubmitting = false, submittedEmail = email) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            }
        }
    }
}
