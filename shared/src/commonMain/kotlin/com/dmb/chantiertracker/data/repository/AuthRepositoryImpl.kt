package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.ChangePasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.ForgotPasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.LoginRequestDto
import com.dmb.chantiertracker.data.remote.dto.RegisterRequestDto
import com.dmb.chantiertracker.data.remote.dto.ResendCodeRequestDto
import com.dmb.chantiertracker.data.remote.dto.ResetPasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.UserResponseDto
import com.dmb.chantiertracker.data.remote.dto.VerifyEmailRequestDto
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
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
        val tokens = tokenStorage.get()
        if (tokens == null) {
            authStateHolder.update(AuthState.Unauthenticated)
            return
        }
        try {
            val user = apiCall { api.me() }.toUser()
            authStateHolder.update(AuthState.Authenticated(user))
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.MustChangePassword) {
            // The token itself is still valid (MustChangePasswordFilter runs
            // after JwtFilter, on an already-authenticated principal) — never
            // clear it here, that would strand the account: /auth/change-password
            // is the only way out, and it needs this exact token.
            val email = decodeJwtEmail(tokens.accessToken)
            if (email != null) {
                authStateHolder.update(AuthState.MustChangePassword(email))
            } else {
                tokenStorage.clear()
                authStateHolder.update(AuthState.Unauthenticated)
            }
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
        onboardingStore.markFirstLoginCompleted()
        try {
            val user = apiCall { api.me() }.toUser()
            authStateHolder.update(AuthState.Authenticated(user))
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.MustChangePassword) {
            // Already have the email as a parameter here — no need to decode
            // the token, unlike bootstrap()'s cold start.
            authStateHolder.update(AuthState.MustChangePassword(email))
        }
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

    // The generic 400-without-field-errors mapping (InvalidCode) would say
    // "invalid or expired code" for a wrong current password — nonsensical.
    // Remapped locally, same posture as BillingRepositoryImpl/AdminRepositoryImpl.
    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        try {
            apiCall { api.changePassword(ChangePasswordRequestDto(currentPassword, newPassword)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.InvalidCode) {
            throw DomainException.InvalidCurrentPassword
        }
    }
}

private fun UserResponseDto.toUser(): User = User(
    id = id,
    email = email,
    name = name,
    active = active,
    globalRole = globalRole.toGlobalRole(),
)

// internal, not private: reused by AdminRepositoryImpl for the same field on
// AdminUserResponseDto (ADR-52) — one tolerant mapping, never GlobalRole.valueOf().
internal fun String.toGlobalRole(): GlobalRole = when (uppercase()) {
    "USER" -> GlobalRole.USER
    "SUPER_ADMIN" -> GlobalRole.SUPER_ADMIN
    else -> GlobalRole.UNKNOWN
}
