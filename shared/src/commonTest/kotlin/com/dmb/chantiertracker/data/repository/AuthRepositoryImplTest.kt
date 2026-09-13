package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val USER_JSON =
    """{"id":7,"email":"jean@chantier.dev","name":"Jean","active":true,"globalRole":"USER"}"""
private const val TOKENS_JSON =
    """{"accessToken":"access-1","refreshToken":"refresh-1"}"""

private class Fixture(
    val storage: FakeTokenStorage = FakeTokenStorage(),
    val holder: AuthStateHolder = AuthStateHolder(),
    val onboarding: FakeOnboardingStore = FakeOnboardingStore(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val mock = RecordingMockClient(storage, handler = handler)
    val repo = AuthRepositoryImpl(AuthApi(mock.client), storage, holder, onboarding)
}

class AuthRepositoryImplTest {

    @Test
    fun register_posts_credentials() = runTest {
        val f = Fixture {
            assertEquals("/api/v1/auth/register", it.url.encodedPath)
            respondJson("""{"id":1,"email":"a@b.dev"}""", HttpStatusCode.Created)
        }
        f.repo.register("a@b.dev", "Password1234!", "Alice")
        val body = f.mock.requests.single().body.toByteArray().decodeToString()
        assertTrue("a@b.dev" in body && "Alice" in body)
    }

    @Test
    fun register_maps_409_to_email_already_used() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.Conflict, "Cette adresse email est déjà utilisée.") }
        assertFailsWith<DomainException.EmailAlreadyUsed> {
            f.repo.register("a@b.dev", "Password1234!", "A")
        }
    }

    @Test
    fun register_maps_400_with_errors_to_validation() = runTest {
        val f = Fixture {
            respondProblem(HttpStatusCode.BadRequest, "Données invalides.", mapOf("password" to "Trop court."))
        }
        assertFailsWith<DomainException.Validation> { f.repo.register("a@b.dev", "x", "A") }
    }

    @Test
    fun login_stores_tokens_and_sets_authenticated() = runTest {
        val f = Fixture {
            when (it.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson(TOKENS_JSON)
                "/api/v1/users/me" ->
                    if (it.headers[HttpHeaders.Authorization] == "Bearer access-1") respondJson(USER_JSON)
                    else respondProblem(HttpStatusCode.Unauthorized, "Authentification requise.")
                else -> error("unexpected ${it.url}")
            }
        }
        f.repo.login("jean@chantier.dev", "Password1234!")
        assertEquals(AuthTokens("access-1", "refresh-1"), f.storage.tokens)
        val state = f.holder.state.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("Jean", state.user.name)
        assertTrue(f.mock.requests.any { it.url.encodedPath == "/api/v1/users/me" })
    }

    @Test
    fun successful_login_marks_first_login_completed() = runTest {
        val f = Fixture {
            when (it.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson(TOKENS_JSON)
                "/api/v1/users/me" -> respondJson(USER_JSON)
                else -> error("unexpected ${it.url}")
            }
        }
        assertEquals(false, f.repo.hasCompletedFirstLogin())
        f.repo.login("jean@chantier.dev", "Password1234!")
        assertEquals(true, f.repo.hasCompletedFirstLogin())
    }

    @Test
    fun failed_login_does_not_mark_first_login_completed() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.Unauthorized, "Email ou mot de passe incorrect.") }
        assertFailsWith<DomainException.InvalidCredentials> { f.repo.login("a@b.dev", "bad") }
        assertEquals(false, f.repo.hasCompletedFirstLogin())
    }

    @Test
    fun login_after_password_reset_uses_the_new_token_not_the_stale_stored_one() = runTest {
        // Scénario réel : un ancien token traîne dans le storage (session précédente,
        // révoquée côté serveur par le reset). Le nouveau login doit s'authentifier
        // avec le token FRAÎCHEMENT émis, pas l'ancien.
        val f = Fixture(storage = FakeTokenStorage(AuthTokens("revoked-access", "revoked-refresh"))) {
            when (it.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson("""{"accessToken":"new-access","refreshToken":"new-refresh"}""")
                "/api/v1/auth/refresh-token" -> respondProblem(HttpStatusCode.Unauthorized, "Refresh token invalide ou expiré.")
                "/api/v1/users/me" ->
                    if (it.headers[HttpHeaders.Authorization] == "Bearer new-access") respondJson(USER_JSON)
                    else respondProblem(HttpStatusCode.Unauthorized, "Authentification requise.")
                else -> error("unexpected ${it.url}")
            }
        }
        f.repo.login("jean@chantier.dev", "NewPassword1234!")
        assertIs<AuthState.Authenticated>(f.holder.state.value)
        assertEquals(AuthTokens("new-access", "new-refresh"), f.storage.tokens)
    }

    @Test
    fun login_maps_401_to_invalid_credentials() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.Unauthorized, "Email ou mot de passe incorrect.") }
        assertFailsWith<DomainException.InvalidCredentials> { f.repo.login("a@b.dev", "bad") }
    }

    @Test
    fun login_maps_403_locked_to_account_locked() = runTest {
        val f = Fixture {
            respondProblem(HttpStatusCode.Forbidden, "Ce compte est temporairement verrouillé suite à plusieurs tentatives.")
        }
        assertFailsWith<DomainException.AccountLocked> { f.repo.login("a@b.dev", "bad") }
    }

    @Test
    fun login_maps_403_unverified_to_account_not_verified() = runTest {
        val f = Fixture {
            respondProblem(HttpStatusCode.Forbidden, "Votre compte n'est pas encore activé. Vérifiez votre email.")
        }
        assertFailsWith<DomainException.AccountNotVerified> { f.repo.login("a@b.dev", "x") }
    }

    @Test
    fun verify_email_maps_400_to_invalid_code() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.BadRequest, "Code de vérification invalide.") }
        assertFailsWith<DomainException.InvalidCode> { f.repo.verifyEmail("a@b.dev", "000000") }
    }

    @Test
    fun logout_clears_tokens_and_sets_unauthenticated_even_on_error() = runTest {
        val f = Fixture(storage = FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.Unauthorized, "expiré")
        }
        f.repo.logout()
        assertNull(f.storage.tokens)
        assertEquals(AuthState.Unauthenticated, f.holder.state.value)
    }

    @Test
    fun bootstrap_without_token_is_unauthenticated() = runTest {
        val f = Fixture { error("no call expected") }
        f.repo.bootstrap()
        assertEquals(AuthState.Unauthenticated, f.holder.state.value)
    }

    @Test
    fun bootstrap_with_valid_token_is_authenticated() = runTest {
        val f = Fixture(storage = FakeTokenStorage(AuthTokens("a", "r"))) {
            assertEquals("/api/v1/users/me", it.url.encodedPath)
            assertEquals("Bearer a", it.headers[HttpHeaders.Authorization])
            respondJson(USER_JSON)
        }
        f.repo.bootstrap()
        assertIs<AuthState.Authenticated>(f.holder.state.value)
    }

    @Test
    fun login_with_must_change_password_sets_that_state_not_authenticated() = runTest {
        val f = Fixture {
            when (it.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson(TOKENS_JSON)
                "/api/v1/users/me" ->
                    respondProblem(HttpStatusCode.Forbidden, "Vous devez changer votre mot de passe avant de continuer.")
                else -> error("unexpected ${it.url}")
            }
        }
        f.repo.login("jean@chantier.dev", "Temp1234!Pass")
        assertEquals(AuthState.MustChangePassword("jean@chantier.dev"), f.holder.state.value)
        // The tokens the block just issued must be kept — /auth/change-password
        // needs them, they are not invalid, just restricted.
        assertEquals(AuthTokens("access-1", "refresh-1"), f.storage.tokens)
    }

    @Test
    fun bootstrap_with_must_change_password_decodes_the_email_from_the_stored_token() = runTest {
        // header {"alg":"HS256","typ":"JWT"}, payload {"sub":"admin@chantier.dev"}
        val token = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
            "eyJzdWIiOiAiYWRtaW5AY2hhbnRpZXIuZGV2In0.sig"
        val f = Fixture(storage = FakeTokenStorage(AuthTokens(token, "r"))) {
            respondProblem(HttpStatusCode.Forbidden, "Vous devez changer votre mot de passe avant de continuer.")
        }
        f.repo.bootstrap()
        assertEquals(AuthState.MustChangePassword("admin@chantier.dev"), f.holder.state.value)
        // The token is still valid server-side (the filter runs on an already
        // -authenticated principal) — never clear it here, that would strand
        // the account: /auth/change-password is the only way out.
        assertEquals(AuthTokens(token, "r"), f.storage.tokens)
    }

    @Test
    fun bootstrap_with_must_change_password_and_an_undecodable_token_falls_back_to_unauthenticated() = runTest {
        val f = Fixture(storage = FakeTokenStorage(AuthTokens("not-a-real-jwt", "r"))) {
            respondProblem(HttpStatusCode.Forbidden, "Vous devez changer votre mot de passe avant de continuer.")
        }
        f.repo.bootstrap()
        assertEquals(AuthState.Unauthenticated, f.holder.state.value)
        assertNull(f.storage.tokens)
    }

    @Test
    fun change_password_posts_both_passwords() = runTest {
        val f = Fixture {
            assertEquals("/api/v1/auth/change-password", it.url.encodedPath)
            val body = it.body.toByteArray().decodeToString()
            assertTrue("oldPass1234!" in body && "NewPass1234!" in body)
            respondJson("", HttpStatusCode.NoContent)
        }
        f.repo.changePassword("oldPass1234!", "NewPass1234!")
    }

    @Test
    fun change_password_maps_400_to_invalid_current_password_not_invalid_code() = runTest {
        // InvalidCurrentPasswordException is a 400 with no field errors —
        // apiCall's generic mapping would otherwise call this "invalid or
        // expired code", nonsensical for a wrong current password.
        val f = Fixture { respondProblem(HttpStatusCode.BadRequest, "Le mot de passe actuel est incorrect.") }
        assertFailsWith<DomainException.InvalidCurrentPassword> {
            f.repo.changePassword("wrong", "NewPass1234!")
        }
    }
}
