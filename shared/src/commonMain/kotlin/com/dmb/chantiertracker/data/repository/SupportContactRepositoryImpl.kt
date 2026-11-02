package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.SupportContactDao
import com.dmb.chantiertracker.data.local.db.SupportContactEntity
import com.dmb.chantiertracker.data.remote.LegalSettingsApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.domain.repository.SupportContactRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupportContactRepositoryImpl(
    private val api: LegalSettingsApi,
    private val dao: SupportContactDao,
    private val clock: Clock = SystemClock,
) : SupportContactRepository {

    override fun observeEmail(): Flow<String?> = dao.observe().map { it?.email }

    override suspend fun refresh() {
        val dto = try {
            apiCall { api.get() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        dao.upsert(SupportContactEntity(email = dto.supportContactEmail?.trim()?.takeIf { it.isNotEmpty() }, refreshedAt = clock.nowEpochMillis()))
    }
}
