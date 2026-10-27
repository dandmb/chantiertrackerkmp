package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(val email: String, val password: String, val name: String)

@Serializable
data class RegisterResponseDto(val id: Long, val email: String)

@Serializable
data class VerifyEmailRequestDto(val email: String, val code: String)

@Serializable
data class ResendCodeRequestDto(val email: String)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class LoginResponseDto(val accessToken: String, val refreshToken: String)

@Serializable
data class RefreshTokenRequestDto(val refreshToken: String)

@Serializable
data class ForgotPasswordRequestDto(val email: String)

@Serializable
data class ResetPasswordRequestDto(val email: String, val code: String, val newPassword: String)

@Serializable
data class ChangePasswordRequestDto(val currentPassword: String, val newPassword: String)

@Serializable
data class UserResponseDto(
    val id: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val globalRole: String,
    val phone: String? = null,
    val language: String? = null,
)

@Serializable
data class UpdateProfileRequestDto(val name: String, val phone: String, val language: String)

@Serializable
data class UpdateLanguageRequestDto(val language: String)

@Serializable
data class ProblemDetailDto(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val code: String? = null,
    @SerialName("errors") val errors: Map<String, String>? = null,
    val retryAfterSeconds: Int? = null,
)
