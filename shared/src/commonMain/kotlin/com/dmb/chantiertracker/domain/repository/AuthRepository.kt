package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun hasCompletedFirstLogin(): Boolean
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun markOnboardingSeen()
    suspend fun bootstrap()
    suspend fun register(email: String, password: String, name: String)
    suspend fun verifyEmail(email: String, code: String)
    suspend fun resendCode(email: String)
    suspend fun login(email: String, password: String)
    suspend fun logout()
    suspend fun forgotPassword(email: String)
    suspend fun resetPassword(email: String, code: String, newPassword: String)
}
