package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.ForgotPasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.LoginRequestDto
import com.dmb.chantiertracker.data.remote.dto.RegisterRequestDto
import com.dmb.chantiertracker.data.remote.dto.ResendCodeRequestDto
import com.dmb.chantiertracker.data.remote.dto.ResetPasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.UserResponseDto
import com.dmb.chantiertracker.data.remote.dto.VerifyEmailRequestDto
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokenStorage: TokenStorage,
    private val authStateHolder: AuthStateHolder,
    private val onboardingStore: OnboardingStore,
) : AuthRepository {

    override val authState: StateFlow<AuthState> = authStateHolder.state

    override suspend fun hasCompletedFirstLogin(): Boolean = onboardingStore.hasCompletedFirstLogin()

    override suspend fun hasSeenOnboarding(): Boolean = onboardingStore.hasSeenOnboarding()

    override suspend fun markOnboardingSeen() = onboardingStore.markOnboardingSeen()

    override suspend fun bootstrap() {
        if (tokenStorage.get() == null) {
            authStateHolder.update(AuthState.Unauthenticated)
            return
        }
        try {
            val user = apiCall { api.me() }.toUser()
            authStateHolder.update(AuthState.Authenticated(user))
        } catch (e: Throwable) {
            tokenStorage.clear()
            authStateHolder.update(AuthState.Unauthenticated)
        }
    }

    override suspend fun register(email: String, password: String, name: String) {
        apiCall { api.register(RegisterRequestDto(email, password, name)) }
    }

    override suspend fun verifyEmail(email: String, code: String) {
        apiCall { api.verifyEmail(VerifyEmailRequestDto(email, code)) }
    }

    override suspend fun resendCode(email: String) {
        apiCall { api.resendCode(ResendCodeRequestDto(email)) }
    }

    override suspend fun login(email: String, password: String) {
        val tokens = apiCall { api.login(LoginRequestDto(email, password)) }
        tokenStorage.save(AuthTokens(tokens.accessToken, tokens.refreshToken))
        val user = apiCall { api.me() }.toUser()
        onboardingStore.markFirstLoginCompleted()
        authStateHolder.update(AuthState.Authenticated(user))
    }

    override suspend fun logout() {
        runCatching { apiCall { api.logout() } }
        tokenStorage.clear()
        authStateHolder.update(AuthState.Unauthenticated)
    }

    override suspend fun forgotPassword(email: String) {
        apiCall { api.forgotPassword(ForgotPasswordRequestDto(email)) }
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        apiCall { api.resetPassword(ResetPasswordRequestDto(email, code, newPassword)) }
    }
}

private fun UserResponseDto.toUser(): User = User(
    id = id,
    email = email,
    name = name,
    active = active,
    globalRole = when (globalRole.uppercase()) {
        "USER" -> GlobalRole.USER
        "SUPER_ADMIN" -> GlobalRole.SUPER_ADMIN
        else -> GlobalRole.UNKNOWN
    },
)
