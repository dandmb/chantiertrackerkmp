package com.dmb.chantiertracker.domain.model

/**
 * A readable "who did what, where" trace of significant project actions —
 * project (create/delete/status), stage (create/delete/budget), purchase and
 * consumption lines (create/edit/delete). Never the daily entries themselves,
 * members, or renames. Mirrors the backend `historique-modification.md`.
 */
data class ModificationHistoryItem(
    val id: Long,
    // The backend `LocalDateTime` as sent, zoneless server-zone string
    // (`2026-09-05T14:32:11.123`). Formatted for display in the presentation
    // layer, never parsed here — same as every other timestamp in the app.
    val modifiedAt: String,
    val actionType: HistoryActionType,
    // A ready-made sentence built server-side (actor name + formatted
    // amounts/quantities included). Null only for legacy rows recorded before
    // the backend added this column — the UI falls back to a generic line.
    val description: String?,
    val entryId: Long?,
    val userId: Long?,
    val fieldName: String?,
    val oldValue: String?,
    val newValue: String?,
)

enum class HistoryActionType { CREATION, MODIFICATION, DELETION, UNKNOWN }

/**
 * One page of project history. `page` is 0-based (the server's `number`).
 * `isFirst`/`isLast` drive the Previous/Next buttons; the page size is a data
 * layer concern the caller never needs to know.
 */
data class HistoryPage(
    val items: List<ModificationHistoryItem>,
    val page: Int,
    val totalPages: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val totalElements: Int,
)

/**
 * The user-facing sort choices, each mapping to the backend's `sort`/`order`
 * pair (`sort` ∈ {date, action}, `order` ∈ {asc, desc}) — the single place that
 * translation lives, mirroring the web's `resolveHistorySort`. Labels are
 * resolved via i18n in the presentation layer.
 */
enum class HistorySort(val apiSort: String, val apiOrder: String) {
    NEWEST_FIRST("date", "desc"),
    OLDEST_FIRST("date", "asc"),
    BY_ACTION("action", "asc"),
}
