package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PlanUsageDao
import com.dmb.chantiertracker.data.local.db.PlanUsageEntity
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val api: AccountApi,
    private val dao: PlanUsageDao,
    private val clock: Clock = SystemClock,
) : AccountRepository {

    override fun observePlanUsage(): Flow<PlanUsage?> =
        dao.observe().map { it?.toPlanUsage() }

    override suspend fun refreshPlanUsage() {
        val dto = try {
            apiCall { api.planUsage() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        dao.upsert(
            PlanUsageEntity(
                plan = dto.plan.uppercase(),
                projectsLimit = dto.projectsLimit,
                refreshedAt = clock.nowEpochMillis(),
                projectsUsed = dto.projectsUsed.toInt(),
                photosUsed = dto.photosUsed.toInt(),
                photosLimit = dto.photosLimit,
                videosUsed = dto.videosUsed.toInt(),
                videosLimit = dto.videosLimit,
                videoDurationLimitSeconds = dto.videoDurationLimitSeconds,
                supervisorsUsed = dto.supervisorsUsed.toInt(),
                supervisorsLimit = dto.supervisorsLimit,
                planExpiresAt = dto.planExpiresAt,
                hasStripeCustomer = dto.hasStripeCustomer,
            ),
        )
    }
}

internal fun String.toPlan(): Plan = when (uppercase()) {
    "FREE" -> Plan.FREE
    "SEMI_FLEX" -> Plan.SEMI_FLEX
    "LIBERTE" -> Plan.LIBERTE
    else -> Plan.UNKNOWN
}

internal fun PlanUsageEntity.toPlanUsage(): PlanUsage = PlanUsage(
    plan = plan.toPlan(),
    projectsLimit = projectsLimit,
    projectsUsed = projectsUsed ?: 0,
    photosUsed = photosUsed ?: 0,
    photosLimit = photosLimit,
    videosUsed = videosUsed ?: 0,
    videosLimit = videosLimit ?: 0,
    videoDurationLimitSeconds = videoDurationLimitSeconds ?: 0,
    supervisorsUsed = supervisorsUsed ?: 0,
    supervisorsLimit = supervisorsLimit,
    planExpiresAt = planExpiresAt,
    hasStripeCustomer = hasStripeCustomer ?: false,
)
