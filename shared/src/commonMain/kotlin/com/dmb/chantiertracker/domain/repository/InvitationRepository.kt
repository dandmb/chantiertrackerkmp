package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.Invitation
import kotlinx.coroutines.flow.Flow

interface InvitationRepository {

    fun observeInvitations(projectLocalId: String): Flow<List<Invitation>>

    /**
     * Pending invitations addressed to the current user (`GET /users/me/invitations`).
     * **Online only** (ADR-34) — held in the ViewModel, not cached in Room.
     * Throws a `DomainException` on failure.
     */
    suspend fun listIncomingInvitations(): List<IncomingInvitation>

    /**
     * Accept an invitation the current user received. **Online only.** On success
     * the project list is re-pulled so the newly joined project appears. Throws a
     * `DomainException` on failure (`NotFound` when the invitation is gone or
     * already accepted, `Forbidden` on an email mismatch, `Network` offline).
     */
    suspend fun acceptInvitation(token: String)

    /** Explicitly decline an invitation the current user received. **Online only.** */
    suspend fun declineInvitation(token: String)

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
