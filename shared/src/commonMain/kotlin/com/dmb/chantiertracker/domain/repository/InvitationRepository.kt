package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Invitation
import kotlinx.coroutines.flow.Flow

interface InvitationRepository {

    fun observeInvitations(projectLocalId: String): Flow<List<Invitation>>

    /**
     * Sends an invitation. **Online only** (ADR-32): the server mints the token
     * and sends the email, so there is no offline queue. Throws a
     * `DomainException` on failure (`Network` when offline, `Forbidden`/
     * `PlanLimitReached` on a server refusal, `NotFound` if the project has
     * never synced). On success the project is re-pulled so the new pending
     * invitation shows up.
     */
    suspend fun invite(projectLocalId: String, email: String)

    /** Cancels a pending invitation. **Online only** (ADR-32). */
    suspend fun cancelInvitation(projectLocalId: String, invitationId: Long)
}
