package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PlanUsageDao
import com.dmb.chantiertracker.data.local.db.PlanUsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlanUsageDao(initial: PlanUsageEntity? = null) : PlanUsageDao {

    private val row = MutableStateFlow(initial)

    override fun observe(): Flow<PlanUsageEntity?> = row

    override suspend fun upsert(entity: PlanUsageEntity) {
        row.value = entity
    }
}
