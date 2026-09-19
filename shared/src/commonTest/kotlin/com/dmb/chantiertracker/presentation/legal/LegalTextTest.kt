package com.dmb.chantiertracker.presentation.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegalTextTest {

    @Test
    fun each_line_is_a_paragraph_unless_it_starts_with_the_bullet_prefix() {
        val blocks = parseLegalBlocks("Intro :\n- premier point ;\n- second point.\nConclusion.")

        assertEquals(
            listOf(
                LegalBlock.Paragraph("Intro :"),
                LegalBlock.Bullet("premier point ;"),
                LegalBlock.Bullet("second point."),
                LegalBlock.Paragraph("Conclusion."),
            ),
            blocks,
        )
    }

    @Test
    fun a_hyphen_inside_a_line_is_not_mistaken_for_a_bullet() {
        assertEquals(
            listOf(LegalBlock.Paragraph("Row- Level Security"), LegalBlock.Paragraph("-tiret collé")),
            parseLegalBlocks("Row- Level Security\n-tiret collé"),
        )
    }

    @Test
    fun blank_lines_never_become_empty_blocks() {
        assertEquals(listOf(LegalBlock.Paragraph("a"), LegalBlock.Paragraph("b")), parseLegalBlocks("a\n\nb\n"))
    }

    @Test
    fun placeholders_are_replaced_everywhere_they_appear() {
        val text = "Édité par {EDITOR_NAME}, contact {EDITOR_NAME} ou {EDITOR_CONTACT_EMAIL}."

        val resolved = text.withLegalPlaceholders(
            mapOf("{EDITOR_NAME}" to "[À COMPLÉTER : nom]", "{EDITOR_CONTACT_EMAIL}" to "[À COMPLÉTER : e-mail]"),
        )

        assertEquals("Édité par [À COMPLÉTER : nom], contact [À COMPLÉTER : nom] ou [À COMPLÉTER : e-mail].", resolved)
    }

    @Test
    fun an_unknown_token_is_left_untouched_rather_than_swallowed() {
        assertEquals("{UNKNOWN} reste", "{UNKNOWN} reste".withLegalPlaceholders(mapOf("{EDITOR_NAME}" to "x")))
    }

    @Test
    fun a_document_survives_the_navigation_argument_round_trip() {
        LegalDocument.entries.forEach { assertEquals(it, legalDocumentFromArg(it.toArg())) }
        assertNull(legalDocumentFromArg("Nonexistent"))
    }
}
