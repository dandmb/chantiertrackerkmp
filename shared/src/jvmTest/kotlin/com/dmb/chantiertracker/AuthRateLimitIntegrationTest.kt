package com.dmb.chantiertracker

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.DeviceStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * R2 — the per-address budget of forgot-password, reset-password and verify-email (10 per
 * 15 minutes, backend EmailAttemptLimiter) against the real backend, on an address that matches
 * no account: nothing is created. The 8/min/IP limit of the same routes is waited out on the way.
 */
class AuthRateLimitIntegrationTest {

    private val phone = DeviceStack()

    @AfterTest fun cleanUp() = phone.close()

    private suspend fun failureOf(call: suspend () -> Unit): DomainException? =
        try {
            call()
            null
        } catch (e: DomainException) {
            e
        }

    @Test
    fun r2_an_address_that_tried_too_often_waits_with_a_duration_and_is_never_told_its_code_is_wrong() = runBlocking {
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
        val email = "qa-r2-${System.currentTimeMillis()}@local.dev"
        val seen = mutableListOf<DomainException?>()

        // A 429 from the 8/min/IP limit is gone once its Retry-After is waited out; one that is
        // still there right after that wait comes from the address budget (10 per 15 minutes).
        var verify: DomainException? = null
        var waitedOutA429 = false
        while (seen.size < 60) {
            verify = failureOf { phone.auth.verifyEmail(email, "000000") }
            seen += verify
            if (verify is DomainException.RateLimited && waitedOutA429) break
            waitedOutA429 = verify is DomainException.RateLimited
            if (verify is DomainException.RateLimited) delay(((verify.retryAfterSeconds ?: 60) + 1) * 1_000L)
        }
        println("R2 — jusqu'au budget de l'adresse : $seen")
        assertTrue(verify is DomainException.RateLimited && waitedOutA429, "the address budget itself is reached: $seen")
        assertTrue(seen.all { it == DomainException.InvalidCode || it is DomainException.RateLimited }, "only wrong codes, then waits: $seen")
        assertTrue(seen.count { it == DomainException.InvalidCode } >= 10, "at least the ten wrong codes of the budget")
        val forgot = failureOf { phone.auth.forgotPassword(email) }
        val reset = failureOf { phone.auth.resetPassword(email, "000000", "NewPass1234!") }
        println("R2 — après le budget : vérification=$verify, oubli=$forgot, réinitialisation=$reset")

        listOf("vérification" to verify, "mot de passe oublié" to forgot, "réinitialisation" to reset).forEach { (route, failure) ->
            assertIs<DomainException.RateLimited>(failure, "$route : une attente, jamais un code faux")
            assertTrue((failure.retryAfterSeconds ?: 0) > 0, "$route : la durée est connue ($failure)")
        }
    }
}
