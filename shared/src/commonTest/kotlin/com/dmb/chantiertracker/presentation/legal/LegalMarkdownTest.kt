package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.presentation.legal.LegalBlock.Code
import com.dmb.chantiertracker.presentation.legal.LegalBlock.Heading
import com.dmb.chantiertracker.presentation.legal.LegalBlock.ListBlock
import com.dmb.chantiertracker.presentation.legal.LegalBlock.Paragraph
import com.dmb.chantiertracker.presentation.legal.LegalBlock.Quote
import com.dmb.chantiertracker.presentation.legal.LegalBlock.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalMarkdownTest {

    private fun text(value: String) = LegalInline(LegalSpan(value))
    private fun paragraph(value: String) = Paragraph(text(value))

    private fun allSpans(blocks: List<LegalBlock>): List<LegalSpan> = blocks.flatMap { block ->
        when (block) {
            is Heading -> block.text.spans
            is Paragraph -> block.text.spans
            is ListBlock -> block.items.flatMap { allSpans(it) }
            is Quote -> allSpans(block.blocks)
            is Code, Rule -> emptyList()
        }
    }

    @Test
    fun the_sections_of_a_server_document_become_headings_and_paragraphs() {
        val blocks = parseLegalMarkdown("## 1. Éditeur du site\n\nLe site est édité par Jean.\n\n### Détail\n\nDeuxième paragraphe.")

        assertEquals(
            listOf(Heading(2, text("1. Éditeur du site")), paragraph("Le site est édité par Jean."), Heading(3, text("Détail")), paragraph("Deuxième paragraphe.")),
            blocks,
        )
    }

    @Test
    fun the_lines_of_a_paragraph_join_with_a_space_and_a_hard_break_keeps_its_line() {
        val blocks = parseLegalMarkdown("Médiateur : CM2C\nParis\n\nLigne un\\\nLigne deux")

        assertEquals(listOf(paragraph("Médiateur : CM2C Paris"), paragraph("Ligne un\nLigne deux")), blocks)
    }

    @Test
    fun bullet_and_numbered_lists_keep_their_items_and_their_first_number() {
        val blocks = parseLegalMarkdown("- Ton compte.\n- Tes sessions.\n\n3. Trois\n4. Quatre")

        assertEquals(
            listOf(
                ListBlock(ordered = false, start = 1, items = listOf(listOf(paragraph("Ton compte.")), listOf(paragraph("Tes sessions.")))),
                ListBlock(ordered = true, start = 3, items = listOf(listOf(paragraph("Trois")), listOf(paragraph("Quatre")))),
            ),
            blocks,
        )
    }

    @Test
    fun a_nested_list_stays_inside_its_item() {
        val blocks = parseLegalMarkdown("- Données\n  - e-mail\n  - nom")

        val item = (blocks.single() as ListBlock).items.single()
        assertEquals(paragraph("Données"), item.first())
        assertEquals(ListBlock(ordered = false, start = 1, items = listOf(listOf(paragraph("e-mail")), listOf(paragraph("nom")))), item[1])
    }

    @Test
    fun emphasis_strong_and_code_are_kept_as_styles() {
        val spans = (parseLegalMarkdown("_Traduction_ **fait foi** et `code`").single() as Paragraph).text.spans

        assertEquals(
            listOf(
                LegalSpan("Traduction", italic = true),
                LegalSpan(" "),
                LegalSpan("fait foi", bold = true),
                LegalSpan(" et "),
                LegalSpan("code", code = true),
            ),
            spans,
        )
    }

    @Test
    fun the_escaped_brackets_of_a_pending_value_are_shown_without_their_backslash() {
        val blocks = parseLegalMarkdown("Contact : \\[À COMPLÉTER : adresse e\\-mail professionnelle\\].")

        assertEquals(listOf(paragraph("Contact : [À COMPLÉTER : adresse e-mail professionnelle].")), blocks)
    }

    @Test
    fun http_https_and_mailto_links_are_kept_with_their_target() {
        val spans = (
            parseLegalMarkdown("Voir [les CGU](https://chantiertracker.com/cgu), [l'ancien site](http://example.com) ou [écris-nous](mailto:dpo@chantiertracker.com).")
                .single() as Paragraph
            ).text.spans

        assertEquals(
            listOf(
                LegalSpan("Voir "),
                LegalSpan("les CGU", link = LegalLink.Web("https://chantiertracker.com/cgu")),
                LegalSpan(", "),
                LegalSpan("l'ancien site", link = LegalLink.Web("http://example.com")),
                LegalSpan(" ou "),
                LegalSpan("écris-nous", link = LegalLink.Mail("mailto:dpo@chantiertracker.com")),
                LegalSpan("."),
            ),
            spans,
        )
    }

    @Test
    fun reference_links_and_autolinks_are_resolved_too() {
        val blocks = parseLegalMarkdown("Lire [la politique][p] ou <https://chantiertracker.com/cookies> ou <dpo@chantiertracker.com>.\n\n[p]: https://chantiertracker.com/confidentialite")

        val links = allSpans(blocks).mapNotNull { it.link }
        assertEquals(
            listOf(
                LegalLink.Web("https://chantiertracker.com/confidentialite"),
                LegalLink.Web("https://chantiertracker.com/cookies"),
                LegalLink.Mail("mailto:dpo@chantiertracker.com"),
            ),
            links,
        )
        assertEquals(1, blocks.size, "a link definition shows nothing")
    }

    @Test
    fun a_link_to_any_other_scheme_is_shown_as_plain_text_and_never_opens() {
        val blocks = parseLegalMarkdown(
            "[a](javascript:alert(1)) [b](JavaScript:alert(1)) [c](data:text/html,x) [d](/cgu) [e](ftp://x.org) [f](intent://x) [g](file:///etc/passwd)",
        )

        val spans = allSpans(blocks)
        assertTrue(spans.none { it.link != null }, "no link kept: $spans")
        assertEquals("a b c d e f g", (blocks.single() as Paragraph).text.plainText)
    }

    @Test
    fun html_is_shown_as_its_literal_text_and_never_interpreted() {
        val blocks = parseLegalMarkdown("<script>alert(1)</script>\n\nTexte <b>gras</b> <a href=\"https://x.org\">ici</a>.")

        val spans = allSpans(blocks)
        assertTrue(spans.none { it.link != null || it.bold }, "html gives no link nor style: $spans")
        val shown = blocks.joinToString("\n") { (it as Paragraph).text.plainText }
        assertTrue("<script>alert(1)</script>" in shown, shown)
        assertTrue("<b>gras</b>" in shown, shown)
    }

    @Test
    fun an_image_shows_only_its_description_never_its_address() {
        val blocks = parseLegalMarkdown("Avant ![logo du site](https://tracker.example.com/pixel.png) après")

        val paragraph = blocks.single() as Paragraph
        assertEquals("Avant logo du site après", paragraph.text.plainText)
        assertTrue(paragraph.text.spans.none { it.link != null })
    }

    @Test
    fun quotes_code_and_rules_are_rendered_as_plain_blocks() {
        val blocks = parseLegalMarkdown("> Citation\n\n---\n\n```\nligne <b>\n```")

        assertEquals(listOf(Quote(listOf(paragraph("Citation"))), Rule, Code("ligne <b>")), blocks)
    }

    @Test
    fun the_french_and_english_pending_texts_are_found_up_to_their_closing_bracket() {
        val french = "Siège : [À COMPLÉTER : adresse complète]. Tél : [À COMPLÉTER]"
        val english = "Contact: [TO BE COMPLETED: business e-mail address]."

        assertEquals(
            listOf("[À COMPLÉTER : adresse complète]", "[À COMPLÉTER]"),
            pendingRanges(french).map { french.substring(it) },
        )
        assertEquals(listOf("[TO BE COMPLETED: business e-mail address]"), pendingRanges(english).map { english.substring(it) })
        assertEquals(emptyList(), pendingRanges("Rien à compléter [ici]."))
    }

    @Test
    fun an_unclosed_pending_text_is_highlighted_up_to_the_end() {
        val text = "Directeur : [À COMPLÉTER : nom"

        assertEquals(listOf("[À COMPLÉTER : nom"), pendingRanges(text).map { text.substring(it) })
    }
}
