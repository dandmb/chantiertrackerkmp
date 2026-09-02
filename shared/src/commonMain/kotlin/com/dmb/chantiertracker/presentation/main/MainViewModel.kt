package com.dmb.chantiertracker.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val userName: String = "",
    val email: String = "",
    val plan: Plan? = null,
    val isLoggingOut: Boolean = false,
)

class MainViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
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
            runCatching { accountRepository.getCurrentPlan() }
                .onSuccess { plan -> _state.update { it.copy(plan = plan) } }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            authRepository.logout()
        }
    }
}
