package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.ForgotPasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.LoginRequestDto
import com.dmb.chantiertracker.data.remote.dto.LoginResponseDto
import com.dmb.chantiertracker.data.remote.dto.RegisterRequestDto
import com.dmb.chantiertracker.data.remote.dto.RegisterResponseDto
import com.dmb.chantiertracker.data.remote.dto.ResendCodeRequestDto
import com.dmb.chantiertracker.data.remote.dto.ResetPasswordRequestDto
import com.dmb.chantiertracker.data.remote.dto.UserResponseDto
import com.dmb.chantiertracker.data.remote.dto.VerifyEmailRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthApi(private val client: HttpClient) {

    suspend fun register(body: RegisterRequestDto): RegisterResponseDto =
        client.post(ApiRoutes.AUTH_REGISTER) { json(body) }.body()

    suspend fun verifyEmail(body: VerifyEmailRequestDto) {
        client.post(ApiRoutes.AUTH_VERIFY_EMAIL) { json(body) }
    }

    suspend fun resendCode(body: ResendCodeRequestDto) {
        client.post(ApiRoutes.AUTH_RESEND_CODE) { json(body) }
    }

    suspend fun login(body: LoginRequestDto): LoginResponseDto =
        client.post(ApiRoutes.AUTH_LOGIN) { json(body) }.body()

    suspend fun logout() {
        client.post(ApiRoutes.AUTH_LOGOUT)
    }

    suspend fun forgotPassword(body: ForgotPasswordRequestDto) {
        client.post(ApiRoutes.AUTH_FORGOT_PASSWORD) { json(body) }
    }

    suspend fun resetPassword(body: ResetPasswordRequestDto) {
        client.post(ApiRoutes.AUTH_RESET_PASSWORD) { json(body) }
    }

    suspend fun me(): UserResponseDto =
        client.get(ApiRoutes.USERS_ME).body()
}

private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
