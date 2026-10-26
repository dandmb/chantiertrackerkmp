package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.ProblemDetailDto
import com.dmb.chantiertracker.domain.model.DomainException
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

private const val BILLING_NOT_OPEN_CODE = "BILLING_NOT_OPEN"
private const val DUPLICATE_MATERIAL_CODE = "DUPLICATE_MATERIAL"
private const val IDEMPOTENCY_KEY_IN_PROGRESS_CODE = "IDEMPOTENCY_KEY_IN_PROGRESS"
private val PROBLEM_JSON = ContentType("application", "problem+json")

suspend fun <T> apiCall(block: suspend () -> T): T = apiCallReportingRefusalCode({}, block = block)

suspend fun <T> apiCallReportingRefusalCode(onRefusalCode: (String?) -> Unit, onRefusalStatus: (Int) -> Unit = {}, block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DomainException) {
        throw e
    } catch (e: ResponseException) {
        val problem = e.response.problemOrNull()
        // While the API is redeployed the reverse proxy answers 404 in its own format:
        // the server is unreachable, nothing was found missing.
        if (e.response.isNotTheBackendsAnswer(problem)) throw DomainException.ServiceUnavailable
        onRefusalStatus(e.response.status.value)
        throw e.toDomainException(problem, onRefusalCode)
    } catch (e: Throwable) {
        throw DomainException.Network
    }

suspend fun HttpResponse.comesFromAnUnavailableApi(): Boolean = isNotTheBackendsAnswer(problemOrNull())

private suspend fun HttpResponse.problemOrNull(): ProblemDetailDto? = runCatching { body<ProblemDetailDto>() }.getOrNull()

private fun HttpResponse.isNotTheBackendsAnswer(problem: ProblemDetailDto?): Boolean =
    status == HttpStatusCode.NotFound && (contentType()?.match(PROBLEM_JSON) != true || problem?.code.isNullOrBlank())

private fun ResponseException.toDomainException(problem: ProblemDetailDto?, onRefusalCode: (String?) -> Unit): DomainException {
    onRefusalCode(problem?.code)
    val detail = problem?.detail.orEmpty()
    val hasFieldErrors = !problem?.errors.isNullOrEmpty()

    // The backend's machine-readable discriminant (SCREAMING_SNAKE_CASE, derived
    // from the exception name) — tested as is, never the translatable `detail`.
    if (problem?.code == BILLING_NOT_OPEN_CODE) return DomainException.BillingNotOpen
    if (problem?.code == DUPLICATE_MATERIAL_CODE) return DomainException.DuplicateMaterial
    if (problem?.code == IDEMPOTENCY_KEY_IN_PROGRESS_CODE) return DomainException.CreationInProgress(retryAfterSeconds = response.retryAfterSeconds())

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
        HttpStatusCode.PayloadTooLarge -> DomainException.FileTooLarge
        HttpStatusCode.TooManyRequests -> DomainException.RateLimited(retryAfterSeconds = response.retryAfterSeconds())
        HttpStatusCode.BadRequest ->
            if (hasFieldErrors) DomainException.Validation else DomainException.InvalidCode
        else -> DomainException.Unexpected
    }
}

private fun HttpResponse.retryAfterSeconds(): Int? = headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it > 0 }

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it, ignoreCase = true) }
