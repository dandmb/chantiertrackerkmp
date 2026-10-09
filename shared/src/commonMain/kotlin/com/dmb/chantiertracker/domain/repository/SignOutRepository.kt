package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.UnsentWrites

sealed interface SignOutResult {
    data object SignedOut : SignOutResult

    /** Writes not sent yet, and the sync just attempted could not send them (offline, server down). */
    data class Blocked(val unsent: UnsentWrites) : SignOutResult

    /** The sync just succeeded and these still did not leave: refused by the server, or waiting on a refused parent. */
    data class RefusedWritesLeft(val unsent: UnsentWrites) : SignOutResult
}

interface SignOutRepository {
    /**
     * A-1 decision (a): a voluntary sign-out never strands writes that could still be sent.
     * [acceptRefusedWrites] = the user confirmed signing out with writes that will never leave.
     */
    suspend fun signOut(acceptRefusedWrites: Boolean = false): SignOutResult
}
