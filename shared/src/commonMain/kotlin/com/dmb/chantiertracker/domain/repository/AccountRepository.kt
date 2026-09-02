package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Plan

interface AccountRepository {
    suspend fun getCurrentPlan(): Plan
}
