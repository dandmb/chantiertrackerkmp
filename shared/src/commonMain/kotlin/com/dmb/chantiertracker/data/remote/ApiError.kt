package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.ProblemDetailDto
import com.dmb.chantiertracker.domain.model.DomainException
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
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
        HttpStatusCode.Forbidden ->
            if (detail.contains("verrouillé", ignoreCase = true) || detail.contains("locked", ignoreCase = true)) {
                DomainException.AccountLocked
            } else {
                DomainException.AccountNotVerified
            }
        HttpStatusCode.TooManyRequests -> DomainException.RateLimited
        HttpStatusCode.BadRequest ->
            if (hasFieldErrors) DomainException.Validation else DomainException.InvalidCode
        else -> DomainException.Unexpected
    }
}
