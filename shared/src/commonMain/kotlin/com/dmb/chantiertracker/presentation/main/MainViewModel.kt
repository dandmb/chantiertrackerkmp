package com.dmb.chantiertracker.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.SignOutRepository
import com.dmb.chantiertracker.domain.repository.SignOutResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sign-out dialog shows (ADR-69). `null` = no dialog. */
sealed interface LogoutPrompt {
    data object Sending : LogoutPrompt
    data class Blocked(val unsent: UnsentWrites) : LogoutPrompt
    data class RefusedWritesLeft(val unsent: UnsentWrites) : LogoutPrompt
}

data class MainUiState(
    val userName: String = "",
    val email: String = "",
    val plan: Plan? = null,
    val isFounder: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutPrompt: LogoutPrompt? = null,
)

class MainViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val signOutRepository: SignOutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                if (authState is AuthState.Authenticated) {
                    _state.update { it.copy(userName = authState.user.name, email = authState.user.email) }
                }
            }
        }
        viewModelScope.launch {
            accountRepository.observePlanUsage().collect { usage ->
                _state.update { it.copy(plan = usage?.plan, isFounder = usage?.isFounder == true) }
            }
        }
        viewModelScope.launch { accountRepository.refreshPlanUsage() }
    }

    fun logout() = attemptSignOut(acceptRefusedWrites = false)

    fun retryLogout() = attemptSignOut(acceptRefusedWrites = false)

    fun logoutDespiteRefusedWrites() = attemptSignOut(acceptRefusedWrites = true)

    fun dismissLogoutPrompt() = _state.update { it.copy(logoutPrompt = null, isLoggingOut = false) }

    private fun attemptSignOut(acceptRefusedWrites: Boolean) {
        if (_state.value.isLoggingOut) return
        _state.update { it.copy(isLoggingOut = true, logoutPrompt = LogoutPrompt.Sending) }
        viewModelScope.launch {
            val prompt = when (val result = signOutRepository.signOut(acceptRefusedWrites)) {
                SignOutResult.SignedOut -> null
                is SignOutResult.Blocked -> LogoutPrompt.Blocked(result.unsent)
                is SignOutResult.RefusedWritesLeft -> LogoutPrompt.RefusedWritesLeft(result.unsent)
            }
            _state.update { it.copy(isLoggingOut = prompt == null, logoutPrompt = prompt) }
        }
    }
}
