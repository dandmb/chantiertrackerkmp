package com.dmb.chantiertracker.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.validateEmail
import com.dmb.chantiertracker.presentation.auth.validateRequiredPassword
import com.dmb.chantiertracker.presentation.billing.CheckoutLauncher
import com.dmb.chantiertracker.presentation.billing.resolveCheckoutIntent
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
    val checkoutPlan: Plan? = null,
    val checkoutCycle: BillingCycle? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val checkoutLauncher: CheckoutLauncher,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun prefillEmail(email: String) {
        if (_state.value.email.isEmpty()) {
            _state.update { it.copy(email = email) }
        }
    }

    // ADR-50 — carried from PlanSelectionScreen through registration as plain
    // route parameters (see RootNavHost); resolved here the same tolerant way
    // as everywhere else this value is read (resolveCheckoutIntent, never
    // Plan.valueOf()/BillingCycle.valueOf()).
    fun setCheckoutIntent(planArg: String?, cycleArg: String?) {
        val resolved = resolveCheckoutIntent(planArg, cycleArg)
        _state.update { it.copy(checkoutPlan = resolved?.first, checkoutCycle = resolved?.second) }
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
        val checkoutPlan = current.checkoutPlan
        val checkoutCycle = current.checkoutCycle
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null, needsVerification = false) }
            try {
                authRepository.login(current.email.trim(), current.password)
                // ADR-50 — authRepository.login()'s last step flips AuthState to
                // Authenticated, which RootNavHost reacts to by tearing this
                // ViewModel down right away. checkoutLauncher.launch() is a plain
                // (non-suspend) call, so it is guaranteed to run before that
                // teardown can cancel anything here — the checkout itself then
                // proceeds on CheckoutLauncher's own application-lifetime scope,
                // not viewModelScope. See CheckoutLauncher's doc.
                if (checkoutPlan != null && checkoutCycle != null) {
                    checkoutLauncher.launch(checkoutPlan, checkoutCycle)
                }
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
