package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.remote.AdminApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.dto.CreateAdminUserRequestDto
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.data.remote.httpClientEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Configuration of the real-backend tests, all passed as `-Dchantiertracker.*` (forwarded by
 * shared/build.gradle.kts). The super-admin credentials never live in this repository: pass them
 * from the backend's own `.env` (`chantiertracker.adminEmail`, `chantiertracker.adminPassword`).
 */
object IntegrationBackend {
    val enabled: Boolean get() = System.getProperty("chantiertracker.integrationTests") == "true"
    val baseUrl: String get() = System.getProperty("chantiertracker.integrationBaseUrl") ?: "http://localhost:8080/api/v1"
    internal val adminEmail: String? get() = System.getProperty("chantiertracker.adminEmail")?.takeIf { it.isNotBlank() }
    internal val adminPassword: String? get() = System.getProperty("chantiertracker.adminPassword")?.takeIf { it.isNotBlank() }
    val adminConfigured: Boolean get() = adminEmail != null && adminPassword != null
}

fun skipUnless(condition: Boolean, reason: String) = org.junit.Assume.assumeTrue(reason, condition)

fun skipUnlessRealBackendScenariosAreEnabled() = skipUnless(
    IntegrationBackend.enabled && IntegrationBackend.adminConfigured,
    "scénario contre le vrai backend : -Dchantiertracker.integrationTests=true et identifiants super-admin requis",
)

/**
 * The sensitive endpoints share one 8/min/IP bucket on the backend: a run that signs in several
 * times would otherwise fail on a real, intended 429. Waits the server's own Retry-After.
 */
suspend fun <T> retryingOnRateLimit(block: suspend () -> T): T {
    repeat(3) {
        try {
            return block()
        } catch (e: DomainException.RateLimited) {
            delay(((e.retryAfterSeconds ?: 60) + 1) * 1_000L)
        }
    }
    return block()
}

/**
 * Throwaway accounts for the real-backend tests, created by the super-admin and deleted on
 * [close]. Created by an admin, an account never goes through e-mail verification, so it never
 * takes one of the 200 founder seats (FounderProgramService only runs on verify-email) — the
 * self-registration path did, three seats per run, and left the accounts behind.
 */
class DisposableAccounts : AutoCloseable {

    data class Account(val id: Long, val email: String, val name: String, val temporaryPassword: String)

    private val adminStorage = FakeTokenStorage()
    private val client = createHttpClient(httpClientEngine(), adminStorage, IntegrationBackend.baseUrl, false) {}
    private val adminAuth = AuthRepositoryImpl(AuthApi(client), adminStorage, AuthStateHolder(), FakeOnboardingStore(), FakeSyncer(), testOwnership())
    private val admin = AdminApi(client)
    private val created = mutableListOf<Long>()

    suspend fun create(prefix: String, name: String): Account {
        if (adminStorage.tokens == null) {
            retryingOnRateLimit { adminAuth.login(IntegrationBackend.adminEmail!!, IntegrationBackend.adminPassword!!) }
        }
        val email = "$prefix-${System.currentTimeMillis()}@local.dev"
        val temporaryPassword = "TempPass1234!"
        val dto = admin.createUser(CreateAdminUserRequestDto(email, name, temporaryPassword, "USER"))
        created += dto.id
        return Account(dto.id, email, name, temporaryPassword)
    }

    /** Admin plan grant (indefinite) — e.g. to lift a plan limit mid-scenario. */
    suspend fun grantPlan(id: Long, plan: String) {
        admin.updatePlan(id, com.dmb.chantiertracker.data.remote.dto.UpdateUserPlanRequestDto(plan))
    }

    /** Deletes, on [close], an account created some other way (self-registration). */
    fun track(id: Long) { created += id }

    // A failed deletion is retried once with a fresh admin session and never swallowed
    // silently: a leftover account would take a test e-mail and skew the next run's counts.
    override fun close() {
        runBlocking {
            created.forEach { id ->
                val first = runCatching { admin.deleteUser(id) }
                if (first.isFailure) {
                    val retry = runCatching {
                        retryingOnRateLimit { adminAuth.login(IntegrationBackend.adminEmail!!, IntegrationBackend.adminPassword!!) }
                        admin.deleteUser(id)
                    }
                    if (retry.isFailure) {
                        println("NETTOYAGE ÉCHOUÉ pour le compte $id : ${first.exceptionOrNull()} puis ${retry.exceptionOrNull()}")
                    }
                }
            }
        }
        client.close()
    }
}

/**
 * An admin-created account must change its temporary password before anything else
 * (mustChangePassword, ADR-53) — and that change revokes every session, hence the second sign-in.
 */
suspend fun AuthRepository.signInForTheFirstTime(account: DisposableAccounts.Account, password: String) {
    retryingOnRateLimit { login(account.email, account.temporaryPassword) }
    check(authState.value is AuthState.MustChangePassword) { "an admin-created account starts with mustChangePassword" }
    changePassword(account.temporaryPassword, password)
    retryingOnRateLimit { login(account.email, password) }
    check(authState.value is AuthState.Authenticated) { "signed in after the forced change" }
}
