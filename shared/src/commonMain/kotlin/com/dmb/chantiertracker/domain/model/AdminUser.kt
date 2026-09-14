package com.dmb.chantiertracker.domain.model

/**
 * A row of the platform-wide user list (SUPER_ADMIN only, ADR-52). Mirrors
 * the backend's `AdminUserResponse` — verified against
 * `docs/walkthrough/admin-utilisateurs.md` and the real controller.
 */
data class AdminUser(
    val id: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val globalRole: GlobalRole,
    val projectCount: Long,
    // The backend `LocalDateTime` as sent, zoneless server-zone string —
    // formatted for display in the presentation layer, never parsed here,
    // same convention as ModificationHistoryItem.modifiedAt.
    val createdAt: String,
    val plan: Plan,
    val planSource: PlanSource?,
    val planExpiresAt: String?,
)

/**
 * Never persisted server-side — derived at read time from whether the user
 * has a live Stripe subscription id (`STRIPE`) or not (`ADMIN_GRANTED`).
 * `null` on a FREE plan: there is nothing to attribute a source to.
 */
enum class PlanSource { STRIPE, ADMIN_GRANTED }

/**
 * One page of the admin user list. `page` is 0-based (the server's
 * `number`) — same shape as `HistoryPage`/`ReportPage`.
 */
data class AdminUserPage(
    val items: List<AdminUser>,
    val page: Int,
    val totalPages: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val totalElements: Int,
)
