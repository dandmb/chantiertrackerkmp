package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.AdminUserPage

/**
 * Platform-wide administration (SUPER_ADMIN only, ADR-52) — online only, no
 * Room cache, no SyncEngine: this is never the device's own data, same
 * posture as HistoryRepository/ReportRepository/BillingRepository. Grows
 * with each sub-step (creation/edit/delete/plan grant/stats).
 */
interface AdminRepository {
    suspend fun listUsers(page: Int): AdminUserPage
}
