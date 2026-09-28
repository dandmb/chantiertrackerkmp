package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.AdminApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.AdminStatsResponseDto
import com.dmb.chantiertracker.data.remote.dto.AdminUserPageDto
import com.dmb.chantiertracker.data.remote.dto.AdminUserResponseDto
import com.dmb.chantiertracker.data.remote.dto.CreateAdminUserRequestDto
import com.dmb.chantiertracker.data.remote.dto.TimeSeriesPointDto
import com.dmb.chantiertracker.data.remote.dto.UpdateAdminUserRequestDto
import com.dmb.chantiertracker.data.remote.dto.UpdateUserPlanRequestDto
import com.dmb.chantiertracker.domain.model.AdminStats
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.AdminUserPage
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanSource
import com.dmb.chantiertracker.domain.model.StatsPoint
import com.dmb.chantiertracker.domain.repository.AdminRepository
import kotlinx.coroutines.CancellationException

// Online only, no Room cache — see AdminRepository / ADR-52. Talks to
// AdminApi directly via apiCall, same posture as HistoryRepositoryImpl/
// ReportRepositoryImpl/BillingRepositoryImpl.
class AdminRepositoryImpl(private val api: AdminApi) : AdminRepository {

    override suspend fun listUsers(page: Int): AdminUserPage =
        apiCall { api.listUsers(page, PAGE_SIZE) }.toAdminUserPage()

    override suspend fun createUser(email: String, name: String, password: String, globalRole: GlobalRole): AdminUser =
        apiCall {
            api.createUser(CreateAdminUserRequestDto(email = email, name = name, password = password, globalRole = globalRole.name))
        }.toAdminUser()

    override suspend fun updateUserName(id: Long, name: String): AdminUser =
        apiCall { api.updateUser(id, UpdateAdminUserRequestDto(name)) }.toAdminUser()

    override suspend fun deleteUser(id: Long) = apiCall { api.deleteUser(id) }

    override suspend fun resetPassword(id: Long) = apiCall { api.resetPassword(id) }

    // apiCall's generic mapping sends every 409 to EmailAlreadyUsed (correct
    // for createUser's real duplicate-email conflict) — nonsensical here: the
    // only 409 this endpoint can return is AccountAlreadyActiveException. The
    // UI already hides "resend activation" once a row is active, so this is
    // only reachable via a race (activated between page load and the tap);
    // remapped to the honest generic message, same posture as
    // BillingRepositoryImpl's local InvalidCode -> Unexpected remap.
    override suspend fun resendActivation(id: Long) {
        try {
            apiCall { api.resendActivation(id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.EmailAlreadyUsed) {
            throw DomainException.Unexpected
        }
    }

    // Same posture as resendActivation above: the only 409 this endpoint can
    // return is StripeSubscriptionActiveException (verified in
    // AdminUserService.updateUserPlan), never a duplicate email — the UI
    // already disables the form proactively once planSource == STRIPE is
    // known, so this is reachable only via a race (the subscription started
    // after the row was loaded). Remapped to the honest generic message.
    override suspend fun updateUserPlan(id: Long, plan: Plan, expiresAt: String?): AdminUser =
        try {
            apiCall { api.updatePlan(id, UpdateUserPlanRequestDto(plan.name, expiresAt)) }.toAdminUser()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.EmailAlreadyUsed) {
            throw DomainException.Unexpected
        }

    // InvalidStatsDateRangeException is a 400 with no field errors (a
    // business rule, not a @Valid violation) — apiCall's generic mapping
    // sends it to InvalidCode ("that code is invalid or expired"), nonsensical
    // for a date range. The UI already validates the same two rules before
    // submitting (validateStatsDateRange), so this is only reachable via a
    // race (e.g. the device clock rolled over "today" mid-session); Validation
    // ("please check the information you entered") is the honest remap here.
    override suspend fun getStats(granularity: Granularity, from: String?, to: String?): AdminStats =
        try {
            apiCall { api.getStats(granularity.name, from, to) }.toAdminStats()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.InvalidCode) {
            throw DomainException.Validation
        }

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

private fun AdminStatsResponseDto.toAdminStats() = AdminStats(
    totalUsers = totalUsers,
    totalProjects = totalProjects,
    registrations = registrations.map(TimeSeriesPointDto::toStatsPoint),
    projectsCreated = projectsCreated.map(TimeSeriesPointDto::toStatsPoint),
)

private fun TimeSeriesPointDto.toStatsPoint() = StatsPoint(bucket, count)

// Tolerant: an unrecognized value resolves to null exactly like the field
// being absent (FREE plan) — never a crash, and there is nothing more
// specific to fall back to (unlike Plan/GlobalRole, PlanSource has no
// UNKNOWN variant of its own; null already carries "nothing to report").
private fun String.toPlanSourceOrNull(): PlanSource? = when (uppercase()) {
    "STRIPE" -> PlanSource.STRIPE
    "ADMIN_GRANTED" -> PlanSource.ADMIN_GRANTED
    else -> null
}
