package com.dmb.chantiertracker.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val email: String = "",
    val platformName: String = "",
    val apiBaseUrl: String = "",
    val isLoggingOut: Boolean = false,
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val getWelcomeMessage: GetWelcomeMessageUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val welcome = getWelcomeMessage()
            _state.update { it.copy(platformName = welcome.platformName, apiBaseUrl = welcome.apiBaseUrl) }
        }
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                if (authState is AuthState.Authenticated) {
                    _state.update { it.copy(userName = authState.user.name, email = authState.user.email) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            authRepository.logout()
        }
    }
}
