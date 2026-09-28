package com.dmb.chantiertracker.presentation.projects

/** Same shortlist the web project dialogs offer (CreateProjectDialog / EditProjectDialog). */
val SUGGESTED_TIMEZONES: List<String> = listOf(
    "Africa/Douala",
    "Africa/Abidjan",
    "Africa/Casablanca",
    "Europe/Paris",
    "Europe/London",
    "America/New_York",
)

/** The shortlist, with [current] prepended when it isn't already in it (so a project's real zone is never dropped). */
fun timezoneOptionsWith(current: String?): List<String> = when {
    current.isNullOrBlank() || current in SUGGESTED_TIMEZONES -> SUGGESTED_TIMEZONES
    else -> listOf(current) + SUGGESTED_TIMEZONES
}
