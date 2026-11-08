package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.core.ErrorReportingScreen
import com.dmb.chantiertracker.core.NonFatalErrors
import kotlinx.coroutines.currentCoroutineContext

import kotlinx.io.IOException
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
private const val PROJECT_EXPORT_EMPTY_CODE = "PROJECT_EXPORT_EMPTY"
private const val NEW_PASSWORD_SAME_AS_CURRENT_CODE = "NEW_PASSWORD_SAME_AS_CURRENT"
private const val IDEMPOTENCY_KEY_IN_PROGRESS_CODE = "IDEMPOTENCY_KEY_IN_PROGRESS"
private const val INVITATION_ALREADY_PENDING_CODE = "INVITATION_ALREADY_PENDING"
private const val CANNOT_INVITE_SELF_CODE = "CANNOT_INVITE_SELF"
private const val ALREADY_PROJECT_MEMBER_CODE = "ALREADY_PROJECT_MEMBER"
private const val ACCOUNT_NOT_VERIFIED_CODE = "ACCOUNT_NOT_VERIFIED"
private const val MEDIA_PROCESSING_BUSY_CODE = "MEDIA_PROCESSING_BUSY"
private const val REQUEST_BODY_TOO_LARGE_CODE = "REQUEST_BODY_TOO_LARGE"
private const val PROJECT_OWNER_REQUIRED_CODE = "PROJECT_OWNER_REQUIRED"
private const val PROJECT_OWNER_CANNOT_LEAVE_CODE = "PROJECT_OWNER_CANNOT_LEAVE"
private const val PLAN_LIMIT_EXCEEDED_CODE = "PLAN_LIMIT_EXCEEDED"
private const val LOCKED_CODE = "LOCKED"
private const val INVITATION_EMAIL_MISMATCH_CODE = "INVITATION_EMAIL_MISMATCH"
private const val INVALID_QUERY_STRING_CODE = "INVALID_QUERY_STRING"
private val PROBLEM_JSON = ContentType("application", "problem+json")

internal var apiLog: (String) -> Unit = ::println

suspend fun <T> apiCall(block: suspend () -> T): T = apiCallReportingRefusalCode({}, block = block)

suspend fun <T> apiCallReportingRefusalCode(
    onRefusalCode: (String?) -> Unit,
    onRefusalStatus: (Int) -> Unit = {},
    onFieldErrors: (Set<String>) -> Unit = {},
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DomainException) {
        throw e
    } catch (e: ResponseException) {
        val problem = e.response.problemOrNull()
        val status = e.response.status.value
        // While the API is redeployed the reverse proxy answers 404 in its own format:
        // the server is unreachable, nothing was found missing.
        val failure = if (e.response.isNotTheBackendsAnswer(problem)) {
            DomainException.ServiceUnavailable
        } else {
            onRefusalStatus(status)
            problem?.errors?.keys?.let(onFieldErrors)
            e.toDomainException(problem, onRefusalCode)
        }
        reportNonFatal(failure, httpStatus = status, errorCode = problem?.code)
        throw failure
    } catch (e: IOException) {
        reportNonFatal(DomainException.Network, cause = e::class.simpleName, networkFailure = true)
        throw DomainException.Network
    } catch (e: Throwable) {
        apiLog("api: unreadable answer (${e::class.simpleName})")
        reportNonFatal(DomainException.Unexpected, cause = e::class.simpleName)
        throw DomainException.Unexpected
    }

private suspend fun reportNonFatal(
    failure: DomainException,
    httpStatus: Int? = null,
    errorCode: String? = null,
    cause: String? = null,
    networkFailure: Boolean = false,
) = NonFatalErrors.report(
    screen = currentCoroutineContext()[ErrorReportingScreen],
    errorType = failure::class.simpleName ?: "DomainException",
    httpStatus = httpStatus,
    errorCode = errorCode,
    cause = cause,
    networkFailure = networkFailure,
)

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
    if (problem?.code == PROJECT_EXPORT_EMPTY_CODE) return DomainException.ProjectExportEmpty
    if (problem?.code == NEW_PASSWORD_SAME_AS_CURRENT_CODE) return DomainException.NewPasswordSameAsCurrent
    if (problem?.code == IDEMPOTENCY_KEY_IN_PROGRESS_CODE) return DomainException.CreationInProgress(retryAfterSeconds = waitOf(problem))
    if (problem?.code == INVITATION_ALREADY_PENDING_CODE) return DomainException.InvitationAlreadyPending
    if (problem?.code == CANNOT_INVITE_SELF_CODE) return DomainException.CannotInviteSelf
    if (problem?.code == ALREADY_PROJECT_MEMBER_CODE) return DomainException.AlreadyProjectMember
    if (problem?.code == ACCOUNT_NOT_VERIFIED_CODE) return DomainException.AccountNotVerified
    if (problem?.code == MEDIA_PROCESSING_BUSY_CODE) return DomainException.RateLimited(retryAfterSeconds = waitOf(problem))
    if (problem?.code == REQUEST_BODY_TOO_LARGE_CODE) return DomainException.ContentTooLarge
    if (problem?.code == PROJECT_OWNER_REQUIRED_CODE) return DomainException.OwnerRequired
    if (problem?.code == PROJECT_OWNER_CANNOT_LEAVE_CODE) return DomainException.OwnerCannotLeave
    if (problem?.code == PLAN_LIMIT_EXCEEDED_CODE) return DomainException.PlanLimitReached
    if (problem?.code == INVITATION_EMAIL_MISMATCH_CODE) return DomainException.InvitationForAnotherAccount
    if (problem?.code == LOCKED_CODE) return DomainException.AccountLocked(retryAfterSeconds = waitOf(problem))
    if (problem?.code == INVALID_QUERY_STRING_CODE) {
        apiLog("api: $INVALID_QUERY_STRING_CODE on ${response.call.request.url.encodedPath}")
        return DomainException.Unexpected
    }

    return when (response.status) {
        HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadGateway, HttpStatusCode.GatewayTimeout -> DomainException.ServiceUnavailable
        HttpStatusCode.Unauthorized -> DomainException.InvalidCredentials
        HttpStatusCode.Conflict -> DomainException.EmailAlreadyUsed
        HttpStatusCode.NotFound -> DomainException.NotFound
        HttpStatusCode.Forbidden -> when {
            detail.containsAny("verrouillé", "locked") -> DomainException.AccountLocked(retryAfterSeconds = waitOf(problem))
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
        HttpStatusCode.TooManyRequests -> DomainException.RateLimited(retryAfterSeconds = waitOf(problem))
        HttpStatusCode.BadRequest ->
            if (hasFieldErrors) DomainException.Validation else DomainException.InvalidCode
        else -> DomainException.Unexpected
    }
}

internal fun HttpResponse.retryAfterSeconds(): Int? = headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it > 0 }

private fun ResponseException.waitOf(problem: ProblemDetailDto?): Int? =
    problem?.retryAfterSeconds?.takeIf { it > 0 } ?: response.retryAfterSeconds()

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it, ignoreCase = true) }
