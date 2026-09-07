package com.dmb.chantiertracker.presentation.projects.history

enum class HistorySegmentKind {
    // Plain running text — the actor, the verbs, connectives.
    PLAIN,

    // A formatted number, with its unit or currency when the backend attached
    // one: "3 tonnes", "500 000 FCFA", "2,5", and each side of a
    // "500 000 → 600 000 EUR" change. The "how much / what quantity".
    VALUE,

    // The element the change is about: "l'étape <name>" / "le projet <name>".
    // The "where".
    TARGET,
}

data class HistorySegment(val text: String, val kind: HistorySegmentKind)

// The history `description` is a full sentence built server-side (see the
// backend's docs/walkthrough/historique-modification.md) — there are NO
// structured fields to lean on. This splits the string back into styleable
// parts by matching the recurring shapes the backend produces.
//
// LIMITS (by design):
//   • Parsing tracks the *current* backend phrasing. A sentence shape it does
//     not recognise stays entirely PLAIN — never a crash, never wrong emphasis.
//   • A stage / project name that itself contains ':' ',' or '.' is cut at that
//     character for the TARGET span; the tail renders plain.
//   • A multi-word or parenthesised unit ("sac (50 kg)") only styles its first
//     token with the number.
//   • French only — the backend builds these sentences in French exclusively.
private val TARGET = Regex("""(?:l['’]étape|le projet) [^:,.\n]+""")

// digit group(s) "1 234" + optional ",5" decimal + optional single trailing
// unit/currency token (letters/³/²/., never a digit, punctuation or an arrow).
private val VALUE = Regex("""\d{1,3}(?: \d{3})*(?:,\d+)?(?: [^\s\d:,.()/→-]+)?""")

// A trailing word that is grammar, not a unit — drop it back to the number.
private val TRAILING_STOPWORDS =
    setOf("de", "à", "a", "du", "des", "le", "la", "les", "sur", "au", "aux", "et", "en", "dans", "par")

fun parseHistoryDescription(description: String): List<HistorySegment> {
    if (description.isBlank()) return listOf(HistorySegment(description, HistorySegmentKind.PLAIN))

    data class Span(val start: Int, val end: Int, val kind: HistorySegmentKind)
    val spans = mutableListOf<Span>()

    for (m in TARGET.findAll(description)) {
        val trimmed = m.value.trimEnd()
        spans += Span(m.range.first, m.range.first + trimmed.length, HistorySegmentKind.TARGET)
    }

    for (m in VALUE.findAll(description)) {
        var value = m.value.trimEnd()
        val lastWord = value.substringAfterLast(' ', missingDelimiterValue = "")
        val dropTrailing = lastWord.isNotEmpty() && lastWord.none { it.isDigit() } &&
            (lastWord.none { it.isLetter() } || lastWord.lowercase() in TRAILING_STOPWORDS)
        if (dropTrailing) value = value.substringBeforeLast(' ')
        if (!value.any { it.isDigit() }) continue
        val start = m.range.first
        val end = start + value.length
        val insideTarget = spans.any { it.kind == HistorySegmentKind.TARGET && start < it.end && end > it.start }
        if (!insideTarget) spans += Span(start, end, HistorySegmentKind.VALUE)
    }

    if (spans.isEmpty()) return listOf(HistorySegment(description, HistorySegmentKind.PLAIN))

    spans.sortBy { it.start }
    val out = mutableListOf<HistorySegment>()
    var cursor = 0
    for (span in spans) {
        if (span.start < cursor) continue
        if (span.start > cursor) {
            out += HistorySegment(description.substring(cursor, span.start), HistorySegmentKind.PLAIN)
        }
        out += HistorySegment(description.substring(span.start, span.end), span.kind)
        cursor = span.end
    }
    if (cursor < description.length) {
        out += HistorySegment(description.substring(cursor), HistorySegmentKind.PLAIN)
    }
    return out
}
