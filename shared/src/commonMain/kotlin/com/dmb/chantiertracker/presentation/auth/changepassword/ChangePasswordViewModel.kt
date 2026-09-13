package com.dmb.chantiertracker.presentation.auth.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateRequiredPassword
import com.dmb.chantiertracker.presentation.auth.validateStrongPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val currentPasswordError: StringResource? = null,
    val newPasswordError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
)

// Reachable only from RootNavHost's AuthState.MustChangePassword branch — see
// that state's doc comment. viewModelScope is safe here even though the
// success path ends in authStateHolder.update(Authenticated), which is what
// causes RootNavHost to unmount this very screen: unlike the Stripe checkout
// case (ADR-50/51, AppScopeCheckoutLauncher), every side effect that matters
// (the password change itself, the token-issuing re-login, the state update)
// already runs to completion strictly *before* that update can trigger the
// unmount — the unmount is a downstream effect of the update succeeding, not
// a race with it. Nothing after the update matters if it's lost to cancellation.
class ChangePasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state = _state.asStateFlow()

    fun onCurrentPasswordChange(value: String) = _state.update {
        it.copy(currentPassword = value, currentPasswordError = null, formError = null)
    }

    fun onNewPasswordChange(value: String) = _state.update {
        it.copy(
            newPassword = value,
            newPasswordError = if (value.isEmpty()) null else validateStrongPassword(value),
            formError = null,
        )
    }

    fun submit(email: String) {
        val current = _state.value
        val currentPasswordError = validateRequiredPassword(current.currentPassword)
        val newPasswordError = validateStrongPassword(current.newPassword)
        if (currentPasswordError != null || newPasswordError != null) {
            _state.update { it.copy(currentPasswordError = currentPasswordError, newPasswordError = newPasswordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                authRepository.changePassword(current.currentPassword, current.newPassword)
                // The backend revokes every active token on a successful
                // change (ChangePasswordService.changePassword) — the token
                // used just above is already dead, log in again to get a
                // fresh one. Success here sets AuthState.Authenticated,
                // which RootNavHost reacts to by leaving this screen.
                authRepository.login(email, current.newPassword)
                _state.update { it.copy(isSubmitting = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isSubmitting = false, formError = DomainException.Unexpected) }
            }
        }
    }

    // The one escape hatch: the backend explicitly allows /auth/logout while
    // mustChangePassword is set (MustChangePasswordFilter.ALLOWED_PATHS), for
    // someone who doesn't have the admin-issued password at hand. The web
    // deliberately offers no such link ("reachable only via the redirect, no
    // cancel button") — a defensible choice there (an address bar is always
    // one manual navigation away), but not on mobile, which has no back
    // button or URL bar to escape a screen with: a genuine dead end without
    // this, until the app is killed and bootstrap() lands here again anyway.
    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
