package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.InvitationDao
import com.dmb.chantiertracker.data.local.db.InvitationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeInvitationDao(initial: List<InvitationEntity> = emptyList()) : InvitationDao {

    private val rows = MutableStateFlow(initial.associateBy { it.id })

    val stored: List<InvitationEntity> get() = rows.value.values.toList()

    override fun observeForProject(projectLocalId: String): Flow<List<InvitationEntity>> =
        rows.map { r -> r.values.filter { it.projectLocalId == projectLocalId }.sortedByDescending { it.createdAt } }

    override suspend fun findForProject(projectLocalId: String): List<InvitationEntity> =
        rows.value.values.filter { it.projectLocalId == projectLocalId }

    override suspend fun upsertAll(invitations: List<InvitationEntity>) {
        rows.value = rows.value + invitations.associateBy { it.id }
    }

    override suspend fun clearForProject(projectLocalId: String) {
        rows.value = rows.value.filterValues { it.projectLocalId != projectLocalId }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value - id
    }
}
