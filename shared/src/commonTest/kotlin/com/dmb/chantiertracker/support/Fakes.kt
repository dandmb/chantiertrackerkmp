package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBuildInfo(override val isDebug: Boolean) : BuildInfo

class FakeTokenStorage(initial: AuthTokens? = null) : TokenStorage {
    var tokens: AuthTokens? = initial
    var clearCount = 0

    override suspend fun get(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null; clearCount++ }
}

class FakeOnboardingStore(initial: Boolean = false, onboardingSeen: Boolean = false) : OnboardingStore {
    var firstLoginCompleted = initial
        private set
    var onboardingSeen = onboardingSeen
        private set

    override suspend fun hasCompletedFirstLogin(): Boolean = firstLoginCompleted
    override suspend fun markFirstLoginCompleted() { firstLoginCompleted = true }
    override suspend fun hasSeenOnboarding(): Boolean = onboardingSeen
    override suspend fun markOnboardingSeen() { onboardingSeen = true }
}

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    var error: Throwable? = null
    var firstLoginCompleted = false
    var onboardingSeen = false
    val calls = mutableListOf<String>()

    fun emitState(state: AuthState) { _authState.value = state }

    private fun record(call: String) {
        calls += call
        error?.let { throw it }
    }

    override suspend fun hasCompletedFirstLogin(): Boolean = firstLoginCompleted
    override suspend fun hasSeenOnboarding(): Boolean = onboardingSeen
    override suspend fun markOnboardingSeen() { onboardingSeen = true; calls += "markOnboardingSeen" }
    override suspend fun bootstrap() { record("bootstrap") }
    override suspend fun register(email: String, password: String, name: String) = record("register:$email:$password:$name")
    override suspend fun verifyEmail(email: String, code: String) = record("verifyEmail:$email:$code")
    override suspend fun resendCode(email: String) = record("resendCode:$email")
    override suspend fun login(email: String, password: String) {
        record("login:$email:$password")
        firstLoginCompleted = true
    }
    override suspend fun logout() = record("logout")
    override suspend fun forgotPassword(email: String) = record("forgotPassword:$email")
    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        record("resetPassword:$email:$code:$newPassword")
}
