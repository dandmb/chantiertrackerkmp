package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.local.db.EditorIdentityDao
import com.dmb.chantiertracker.data.local.db.EditorIdentityEntity
import com.dmb.chantiertracker.data.remote.EditorIdentityApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.domain.repository.EditorIdentityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Read-only, so no SyncEngine: same offline-first shape as AccountRepositoryImpl
// (screens read Room, a failed refresh keeps the last known row).
class EditorIdentityRepositoryImpl(
    private val api: EditorIdentityApi,
    private val dao: EditorIdentityDao,
    private val clock: Clock = SystemClock,
) : EditorIdentityRepository {

    override fun observe(): Flow<EditorIdentity?> = dao.observe().map { it?.toEditorIdentity() }

    override suspend fun refresh() {
        val dto = try {
            apiCall { api.get() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        dao.upsert(
            EditorIdentityEntity(
                firstName = dto.firstName,
                lastName = dto.lastName,
                companyName = dto.companyName,
                legalStatus = dto.legalStatus,
                siret = dto.siret,
                address = dto.address,
                contactEmail = dto.contactEmail,
                vatNumber = dto.vatNumber,
                hostingProviderName = dto.hostingProviderName,
                hostingProviderAddress = dto.hostingProviderAddress,
                refreshedAt = clock.nowEpochMillis(),
            ),
        )
    }
}

internal fun EditorIdentityEntity.toEditorIdentity() = EditorIdentity(
    firstName = firstName,
    lastName = lastName,
    companyName = companyName,
    legalStatus = legalStatus,
    siret = siret,
    address = address,
    contactEmail = contactEmail,
    vatNumber = vatNumber,
    hostingProviderName = hostingProviderName,
    hostingProviderAddress = hostingProviderAddress,
)
