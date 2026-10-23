package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondProblem
import com.dmb.chantiertracker.support.testOwnership
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthCodesAndRateLimitsTest {

    private fun repoAnswering(status: HttpStatusCode, code: String, retryAfter: String? = null): AuthRepositoryImpl {
        val storage = FakeTokenStorage()
        val mock = RecordingMockClient(storage) {
            respondProblem(status, "Refus.", code = code, extraHeaders = retryAfter?.let { mapOf(HttpHeaders.RetryAfter to it) }.orEmpty())
        }
        return AuthRepositoryImpl(AuthApi(mock.client), storage, AuthStateHolder(), FakeOnboardingStore(), FakeSyncer(), testOwnership())
    }

    private suspend fun failureOf(call: suspend () -> Unit): DomainException? =
        try {
            call()
            null
        } catch (e: DomainException) {
            e
        }

    private val emailRateLimited = { repoAnswering(HttpStatusCode.TooManyRequests, "RATE_LIMITED", "42") }

    @Test
    fun a_rate_limited_verification_is_a_wait_of_the_given_time_never_a_wrong_code() = runTest {
        assertEquals(DomainException.RateLimited(42), failureOf { emailRateLimited().verifyEmail("sam@chantier.dev", "123456") })
    }

    @Test
    fun a_rate_limited_resend_of_the_code_is_a_wait_of_the_given_time() = runTest {
        assertEquals(DomainException.RateLimited(42), failureOf { emailRateLimited().resendCode("sam@chantier.dev") })
    }

    @Test
    fun a_rate_limited_forgotten_password_is_a_wait_of_the_given_time() = runTest {
        assertEquals(DomainException.RateLimited(42), failureOf { emailRateLimited().forgotPassword("sam@chantier.dev") })
    }

    @Test
    fun a_rate_limited_reset_is_a_wait_of_the_given_time_never_a_wrong_code() = runTest {
        assertEquals(DomainException.RateLimited(42), failureOf { emailRateLimited().resetPassword("sam@chantier.dev", "123456", "NewPass1234!") })
    }

    @Test
    fun a_wrong_or_invalidated_code_is_still_an_invalid_code() = runTest {
        val wrongCode = { repoAnswering(HttpStatusCode.BadRequest, "INVALID_VERIFICATION_CODE") }
        assertEquals(DomainException.InvalidCode, failureOf { wrongCode().verifyEmail("sam@chantier.dev", "000000") })
        assertEquals(DomainException.InvalidCode, failureOf { wrongCode().resetPassword("sam@chantier.dev", "000000", "NewPass1234!") })
    }
}
