package com.dmb.chantiertracker.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.session.SessionNotice
import com.dmb.chantiertracker.data.session.SessionNotices
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RootViewModel(
    private val authRepository: AuthRepository,
    private val sessionNotices: SessionNotices,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    val sessionNotice: StateFlow<SessionNotice?> = sessionNotices.notice

    private val _hasLoggedInBefore = MutableStateFlow<Boolean?>(null)
    val hasLoggedInBefore: StateFlow<Boolean?> = _hasLoggedInBefore.asStateFlow()

    private val _hasSeenOnboarding = MutableStateFlow<Boolean?>(null)
    val hasSeenOnboarding: StateFlow<Boolean?> = _hasSeenOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            _hasLoggedInBefore.value = authRepository.hasCompletedFirstLogin()
            _hasSeenOnboarding.value = authRepository.hasSeenOnboarding()
            authRepository.bootstrap()
        }
        viewModelScope.launch {
            authRepository.authState.collect {
                _hasLoggedInBefore.value = authRepository.hasCompletedFirstLogin()
                if (it is AuthState.Authenticated) sessionNotices.clear()
            }
        }
    }

    fun markOnboardingSeen() {
        _hasSeenOnboarding.value = true
        viewModelScope.launch { authRepository.markOnboardingSeen() }
    }
}
