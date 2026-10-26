package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.domain.model.DomainException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

private const val SERVER_ERROR = 500

// A creation sent outside the sync engine has no local row to name it: it keeps one key for as
// long as the same request is left without a clear answer, so a resend is a replay, not a duplicate.
class OnlineCreationKeys(private val newKey: () -> String = { Uuid.random().toString() }) {

    private val mutex = Mutex()
    private val keys = mutableMapOf<Any, String>()

    suspend fun <T> send(request: Any, create: suspend (idempotencyKey: String) -> T): T {
        val key = mutex.withLock { keys.getOrPut(request, newKey) }
        var status: Int? = null
        val created = try {
            apiCallReportingRefusalCode({}, { status = it }) { create(key) }
        } catch (e: DomainException) {
            if (!e.leavesTheCreationUnanswered(status)) forget(request)
            throw e
        }
        forget(request)
        return created
    }

    private suspend fun forget(request: Any) = mutex.withLock { keys.remove(request) }

    private fun DomainException.leavesTheCreationUnanswered(status: Int?): Boolean =
        this is DomainException.Network ||
            this is DomainException.ServiceUnavailable ||
            this is DomainException.CreationInProgress ||
            (this is DomainException.Unexpected && (status ?: SERVER_ERROR) >= SERVER_ERROR)
}
