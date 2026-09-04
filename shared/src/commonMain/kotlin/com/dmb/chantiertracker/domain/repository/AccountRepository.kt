package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.PlanUsage
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    /** Last-known plan + project cap from the local store (`null` until first fetched). */
    fun observePlanUsage(): Flow<PlanUsage?>

    /** Best-effort pull of the plan/limit from the server into the local store. Never throws. */
    suspend fun refreshPlanUsage()
}
