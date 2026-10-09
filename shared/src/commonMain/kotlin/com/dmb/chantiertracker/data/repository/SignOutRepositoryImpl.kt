package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.session.UnsyncedWriteCounter
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.isUnreachableServer
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.SignOutRepository
import com.dmb.chantiertracker.domain.repository.SignOutResult

// Signing out never erases anything: the same account finds its data again. The erasure happens
// only when another account signs in (LocalDataOwnership) — hence the guard here, for what is unsent.
class SignOutRepositoryImpl(
    private val authRepository: AuthRepository,
    private val syncer: Syncer,
    private val unsyncedWrites: UnsyncedWriteCounter,
) : SignOutRepository {

    override suspend fun signOut(acceptRefusedWrites: Boolean): SignOutResult {
        if (unsyncedWrites.countUnsynced() == 0) return signedOut()
        val outcome = syncer.syncNow()
        val remaining = unsyncedWrites.unsentByKind()
        return when {
            remaining.total == 0 -> signedOut()
            outcome.leftWritesUnsentForLackOfServer() -> SignOutResult.Blocked(remaining)
            acceptRefusedWrites -> signedOut()
            else -> SignOutResult.RefusedWritesLeft(remaining)
        }
    }

    private fun SyncOutcome.leftWritesUnsentForLackOfServer(): Boolean =
        this is SyncOutcome.Skipped || (this is SyncOutcome.Failed && cause.isUnreachableServer())

    private suspend fun signedOut(): SignOutResult {
        authRepository.logout()
        return SignOutResult.SignedOut
    }
}
