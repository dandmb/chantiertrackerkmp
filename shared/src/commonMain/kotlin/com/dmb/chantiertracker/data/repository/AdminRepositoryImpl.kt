package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.AdminApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.AdminUserPageDto
import com.dmb.chantiertracker.data.remote.dto.AdminUserResponseDto
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.AdminUserPage
import com.dmb.chantiertracker.domain.model.PlanSource
import com.dmb.chantiertracker.domain.repository.AdminRepository

// Online only, no Room cache — see AdminRepository / ADR-52. Talks to
// AdminApi directly via apiCall, same posture as HistoryRepositoryImpl/
// ReportRepositoryImpl/BillingRepositoryImpl.
class AdminRepositoryImpl(private val api: AdminApi) : AdminRepository {

    override suspend fun listUsers(page: Int): AdminUserPage =
        apiCall { api.listUsers(page, PAGE_SIZE) }.toAdminUserPage()

    companion object {
        // Same full-screen size as History/Reports (ADR-44/47) — well under
        // the backend's default Spring Data page-size bound.
        const val PAGE_SIZE = 20
    }
}

private fun AdminUserPageDto.toAdminUserPage() = AdminUserPage(
    items = content.map(AdminUserResponseDto::toAdminUser),
    page = number,
    totalPages = totalPages,
    isFirst = first,
    isLast = last,
    totalElements = totalElements,
)

private fun AdminUserResponseDto.toAdminUser() = AdminUser(
    id = id,
    email = email,
    name = name,
    active = active,
    globalRole = globalRole.toGlobalRole(),
    projectCount = projectCount,
    createdAt = createdAt,
    plan = plan.toPlan(),
    planSource = planSource?.toPlanSourceOrNull(),
    planExpiresAt = planExpiresAt,
)

// Tolerant: an unrecognized value resolves to null exactly like the field
// being absent (FREE plan) — never a crash, and there is nothing more
// specific to fall back to (unlike Plan/GlobalRole, PlanSource has no
// UNKNOWN variant of its own; null already carries "nothing to report").
private fun String.toPlanSourceOrNull(): PlanSource? = when (uppercase()) {
    "STRIPE" -> PlanSource.STRIPE
    "ADMIN_GRANTED" -> PlanSource.ADMIN_GRANTED
    else -> null
}
