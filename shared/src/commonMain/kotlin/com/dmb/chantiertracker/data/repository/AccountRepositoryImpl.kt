package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.AccountRepository

class AccountRepositoryImpl(
    private val api: AccountApi,
) : AccountRepository {

    override suspend fun getCurrentPlan(): Plan = when (apiCall { api.planUsage() }.plan.uppercase()) {
        "FREE" -> Plan.FREE
        "SEMI_FLEX" -> Plan.SEMI_FLEX
        "LIBERTE" -> Plan.LIBERTE
        else -> Plan.UNKNOWN
    }
}
