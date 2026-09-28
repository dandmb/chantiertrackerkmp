package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.ProblemDetailDto
import com.dmb.chantiertracker.domain.model.DomainException
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DomainException) {
        throw e
    } catch (e: ResponseException) {
        throw e.toDomainException()
    } catch (e: Throwable) {
        throw DomainException.Network
    }

private suspend fun ResponseException.toDomainException(): DomainException {
    val problem = runCatching { response.body<ProblemDetailDto>() }.getOrNull()
    val detail = problem?.detail.orEmpty()
    val hasFieldErrors = !problem?.errors.isNullOrEmpty()

    return when (response.status) {
        HttpStatusCode.Unauthorized -> DomainException.InvalidCredentials
        HttpStatusCode.Conflict -> DomainException.EmailAlreadyUsed
        HttpStatusCode.NotFound -> DomainException.NotFound
        HttpStatusCode.Forbidden -> when {
            detail.containsAny("verrouillé", "locked") -> DomainException.AccountLocked
            detail.containsAny("activé", "vérifi", "verif", "activate") -> DomainException.AccountNotVerified
            detail.containsAny("limite", "plan", "palier", "formule", "limit") -> DomainException.PlanLimitReached
            // MustChangePasswordFilter blocks every endpoint but /auth/change-password
            // and /auth/logout with this exact detail — a security filter, not a
            // specific business rule, so any endpoint could in principle return it
            // (same posture as the three cases above), even though in practice only
            // /users/me (called right after login/on bootstrap) ever does.
            detail.containsAny("changer votre mot de passe", "must change your password") ->
                DomainException.MustChangePassword
            else -> DomainException.Forbidden
        }
        HttpStatusCode.TooManyRequests -> DomainException.RateLimited(
            retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it > 0 },
        )
        HttpStatusCode.BadRequest ->
            if (hasFieldErrors) DomainException.Validation else DomainException.InvalidCode
        else -> DomainException.Unexpected
    }
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it, ignoreCase = true) }
