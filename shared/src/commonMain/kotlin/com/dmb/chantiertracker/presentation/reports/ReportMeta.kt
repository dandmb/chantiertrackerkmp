package com.dmb.chantiertracker.presentation.reports

enum class ReportFieldEmphasis { PLAIN, PERSON, DATE }

data class ReportTextSegment(val text: String, val emphasis: ReportFieldEmphasis)

/**
 * Splits an i18n template (`%1$s`, `%2$s`, …) into typed segments, tagging each
 * placeholder with how it should be emphasised.
 *
 * Unlike the history screen — where the server sends a free sentence that
 * `parseHistoryDescription` has to re-split by regex — a report's fields are
 * already structured (`authorName`, `createdAt`, `processedAt` are distinct DTO
 * fields). So there is nothing to parse: the caller just says which placeholder
 * is the person and which is a date, and the screen styles them the same two
 * ways the history screen styles its TARGET / VALUE spans (accent colour for
 * the person, weight for the date).
 *
 * A placeholder with no matching part becomes empty (never throws).
 */
fun reportTextSegments(
    template: String,
    parts: List<Pair<String, ReportFieldEmphasis>>,
): List<ReportTextSegment> {
    val out = mutableListOf<ReportTextSegment>()
    var cursor = 0
    for (match in PLACEHOLDER.findAll(template)) {
        if (match.range.first > cursor) {
            out += ReportTextSegment(template.substring(cursor, match.range.first), ReportFieldEmphasis.PLAIN)
        }
        val (value, emphasis) = parts.getOrElse(match.groupValues[1].toInt() - 1) { "" to ReportFieldEmphasis.PLAIN }
        if (value.isNotEmpty()) out += ReportTextSegment(value, emphasis)
        cursor = match.range.last + 1
    }
    if (cursor < template.length) {
        out += ReportTextSegment(template.substring(cursor), ReportFieldEmphasis.PLAIN)
    }
    return out
}

private val PLACEHOLDER = Regex("%(\\d+)\\\$s")
