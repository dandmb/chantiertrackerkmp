package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.CreateInvitationRequestDto
import com.dmb.chantiertracker.data.remote.dto.InvitationDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.PendingInvitationDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class InvitationApi(private val client: HttpClient) {

    // ADMIN-only server-side — a non-admin GET answers 403.
    suspend fun list(projectId: Long): PageDto<InvitationDto> =
        client.get(ApiRoutes.projectInvitations(projectId)).body()

    suspend fun create(projectId: Long, body: CreateInvitationRequestDto): InvitationDto =
        client.post(ApiRoutes.projectInvitations(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun cancel(id: Long) {
        client.delete(ApiRoutes.invitation(id))
    }

    /** Invitations addressed to the authenticated user (`GET /users/me/invitations`). */
    suspend fun listMine(): List<PendingInvitationDto> =
        client.get(ApiRoutes.USERS_ME_INVITATIONS).body()

    /** Accept as the authenticated user — no body (the new-account fields are web-only). */
    suspend fun accept(token: String) {
        client.post(ApiRoutes.acceptInvitation(token))
    }

    /** Explicitly decline — no body, same access model as accept. */
    suspend fun decline(token: String) {
        client.post(ApiRoutes.declineInvitation(token))
    }
}
