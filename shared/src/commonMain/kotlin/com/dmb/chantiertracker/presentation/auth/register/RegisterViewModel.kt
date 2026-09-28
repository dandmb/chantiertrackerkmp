package com.dmb.chantiertracker.presentation.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateEmail
import com.dmb.chantiertracker.presentation.auth.validateName
import com.dmb.chantiertracker.presentation.auth.validateStrongPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: StringResource? = null,
    val emailError: StringResource? = null,
    val passwordError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val registeredEmail: String? = null,
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null) }
    fun onPasswordChange(value: String) = _state.update {
        it.copy(
            password = value,
            passwordError = if (value.isEmpty()) null else validateStrongPassword(value),
            formError = null,
        )
    }

    fun submit() {
        val current = _state.value
        val nameError = validateName(current.name)
        val emailError = validateEmail(current.email)
        val passwordError = validateStrongPassword(current.password)
        if (nameError != null || emailError != null || passwordError != null) {
            _state.update { it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                val email = current.email.trim()
                authRepository.register(email, current.password, current.name.trim())
                _state.update { it.copy(isSubmitting = false, registeredEmail = email) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            }
        }
    }
}
