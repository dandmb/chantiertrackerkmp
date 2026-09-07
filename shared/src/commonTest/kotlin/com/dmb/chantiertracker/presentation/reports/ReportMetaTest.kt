package com.dmb.chantiertracker.presentation.reports

import kotlin.test.Test
import kotlin.test.assertEquals

class ReportMetaTest {

    private fun List<ReportTextSegment>.text() = joinToString("") { it.text }
    private fun List<ReportTextSegment>.of(e: ReportFieldEmphasis) = filter { it.emphasis == e }.map { it.text }

    @Test
    fun the_author_is_a_person_span_and_the_report_date_a_date_span() {
        val segments = reportTextSegments(
            template = "%1\$s du %2\$s · signalé par %3\$s le %4\$s",
            parts = listOf(
                "Achats" to ReportFieldEmphasis.PLAIN,
                "03-09-2026" to ReportFieldEmphasis.PLAIN,
                "Sam Ferreira" to ReportFieldEmphasis.PERSON,
                "05-09-2026" to ReportFieldEmphasis.DATE,
            ),
        )

        assertEquals("Achats du 03-09-2026 · signalé par Sam Ferreira le 05-09-2026", segments.text())
        assertEquals(listOf("Sam Ferreira"), segments.of(ReportFieldEmphasis.PERSON))
        assertEquals(listOf("05-09-2026"), segments.of(ReportFieldEmphasis.DATE))
        // The flagged entry's own date stays plain — only the report's date is emphasised.
        assertEquals(emptyList(), segments.of(ReportFieldEmphasis.DATE).filter { it == "03-09-2026" })
    }

    @Test
    fun the_processed_line_emphasises_only_the_date() {
        val segments = reportTextSegments(
            template = "Traité le %1\$s",
            parts = listOf("06-09-2026" to ReportFieldEmphasis.DATE),
        )

        assertEquals("Traité le 06-09-2026", segments.text())
        assertEquals(listOf("06-09-2026"), segments.of(ReportFieldEmphasis.DATE))
        assertEquals(listOf("Traité le "), segments.of(ReportFieldEmphasis.PLAIN))
    }

    @Test
    fun an_empty_value_produces_no_segment() {
        val segments = reportTextSegments(
            template = "signalé par %1\$s le %2\$s",
            parts = listOf("" to ReportFieldEmphasis.PERSON, "05-09-2026" to ReportFieldEmphasis.DATE),
        )

        assertEquals("signalé par  le 05-09-2026", segments.text())
        assertEquals(emptyList(), segments.of(ReportFieldEmphasis.PERSON))
    }

    @Test
    fun a_placeholder_with_no_matching_part_becomes_empty_rather_than_crashing() {
        val segments = reportTextSegments("A %1\$s B %2\$s C", parts = listOf("X" to ReportFieldEmphasis.PLAIN))

        assertEquals("A X B  C", segments.text())
    }
}
