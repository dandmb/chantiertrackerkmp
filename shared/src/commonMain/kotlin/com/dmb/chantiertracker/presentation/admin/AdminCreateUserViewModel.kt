package com.dmb.chantiertracker.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.repository.AdminRepository
import com.dmb.chantiertracker.presentation.auth.validateEmail
import com.dmb.chantiertracker.presentation.auth.validateName
import com.dmb.chantiertracker.presentation.auth.validateStrongPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class AdminCreateUserUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val globalRole: GlobalRole = GlobalRole.USER,
    val nameError: StringResource? = null,
    val emailError: StringResource? = null,
    val passwordError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val created: Boolean = false,
)

// Mirrors RegisterViewModel closely (same validators, same submit shape) —
// the difference is entirely in what happens after: this account is active
// immediately, no verification email, mustChangePassword=true server-side
// (POST /admin/users, verified against AdminUserService.createUser).
class AdminCreateUserViewModel(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminCreateUserUiState())
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
    fun onRoleChange(value: GlobalRole) = _state.update { it.copy(globalRole = value, formError = null) }

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
                adminRepository.createUser(
                    email = current.email.trim(),
                    name = current.name.trim(),
                    password = current.password,
                    globalRole = current.globalRole,
                )
                _state.update { it.copy(isSubmitting = false, created = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isSubmitting = false, formError = DomainException.Unexpected) }
            }
        }
    }
}
