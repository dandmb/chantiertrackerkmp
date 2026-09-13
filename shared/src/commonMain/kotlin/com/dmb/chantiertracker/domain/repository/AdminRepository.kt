package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.AdminStats
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.AdminUserPage
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.model.Plan

/**
 * Platform-wide administration (SUPER_ADMIN only, ADR-52) — online only, no
 * Room cache, no SyncEngine: this is never the device's own data, same
 * posture as HistoryRepository/ReportRepository/BillingRepository. Grows
 * with each sub-step (creation/edit/delete/plan grant/stats).
 */
interface AdminRepository {
    suspend fun listUsers(page: Int): AdminUserPage
    suspend fun createUser(email: String, name: String, password: String, globalRole: GlobalRole): AdminUser
    suspend fun updateUserName(id: Long, name: String): AdminUser
    suspend fun deleteUser(id: Long)
    suspend fun resetPassword(id: Long)
    suspend fun resendActivation(id: Long)
    // expiresAt: yyyy-MM-ddTHH:mm:ss or null (indefinite grant). Ignored server-side for FREE.
    suspend fun updateUserPlan(id: Long, plan: Plan, expiresAt: String?): AdminUser
    // from/to: yyyy-MM-dd or null — the backend defaults an omitted bound
    // itself (today / today minus 12 months), same posture as the web
    // (never pre-filled client-side, see AdminDashboardPage.tsx).
    suspend fun getStats(granularity: Granularity, from: String?, to: String?): AdminStats
}
