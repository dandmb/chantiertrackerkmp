package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_stats_date_range_future
import com.dmb.chantiertracker.resources.admin_stats_date_range_order
import org.jetbrains.compose.resources.StringResource

// Mirrors the backend's two rules exactly (AdminStatsService.validateDateRange)
// and the web's client-side re-check (AdminDashboardPage.tsx) — both ISO
// yyyy-MM-dd, safe to compare lexically since the format is fixed-width.
// today is passed in (todayInSystemZone().toString()) rather than computed
// here, so this stays a pure function callers can test with a fixed date.
fun validateStatsDateRange(from: String, to: String, today: String): StringResource? = when {
    to.isNotBlank() && to > today -> Res.string.admin_stats_date_range_future
    from.isNotBlank() && to.isNotBlank() && from > to -> Res.string.admin_stats_date_range_order
    else -> null
}
