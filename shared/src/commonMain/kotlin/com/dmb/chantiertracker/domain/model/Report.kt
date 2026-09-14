package com.dmb.chantiertracker.domain.model

/**
 * A member (typically a SUPERVISOR) flags a problem on an existing daily entry
 * ("something's missing here", "suspicious quantity"…); an ADMIN reviews the
 * list and marks it processed. Deliberately minimal — two statuses, no thread,
 * no severity, no going back. Mirrors the backend `signalements.md`.
 */
data class Report(
    val id: Long,
    // The flagged entry's server id. Kept for identity / a future "open the
    // entry" jump; the list itself never joins it to a local row.
    val entryId: Long,
    // Denormalized server-side at creation (the report has no FK to the entry
    // and must outlive its deletion), so the admin list is self-describing.
    val entryType: EntryType,
    // The flagged entry's day (`yyyy-MM-dd`, server zone). Formatted for display
    // in the presentation layer, never parsed here.
    val entryDate: String,
    // Null once the authoring user is deleted (`ON DELETE SET NULL` server-side).
    val authorName: String?,
    val message: String,
    // Server `LocalDateTime` as sent (`2026-09-07T14:32:11`).
    val createdAt: String,
    val status: ReportStatus,
    // Set together with the PROCESSED status when an admin handles it; null
    // while NEW.
    val processedAt: String?,
)

enum class ReportStatus { NEW, PROCESSED, UNKNOWN }

/**
 * The user-facing sort choices for the project's report list, each mapping to
 * the backend's `sort`/`order` pair (`sort` ∈ {date, status}, `order` ∈ {asc,
 * desc}) — the single place that translation lives, mirroring `HistorySort`.
 * `UNPROCESSED_FIRST` = `status,asc`: the enum is stored as a string server-side
 * so `NEW` < `PROCESSED`, which puts the untreated reports on top. Labels are
 * resolved via i18n in the presentation layer.
 */
enum class ReportSort(val apiSort: String, val apiOrder: String) {
    NEWEST_FIRST("date", "desc"),
    OLDEST_FIRST("date", "asc"),
    UNPROCESSED_FIRST("status", "asc"),
}

/**
 * One page of a project's reports. `page` is 0-based (the server's `number`);
 * `isFirst`/`isLast` drive Previous/Next. Same shape as `HistoryPage` — a
 * paginated, online-only projection.
 */
data class ReportPage(
    val items: List<Report>,
    val page: Int,
    val totalPages: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val totalElements: Int,
)
