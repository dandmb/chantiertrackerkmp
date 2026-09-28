package com.dmb.chantiertracker.presentation

import com.dmb.chantiertracker.presentation.legal.LegalBlock
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.legal.LegalPlaceholder
import com.dmb.chantiertracker.presentation.legal.LegalSectionContent
import com.dmb.chantiertracker.presentation.legal.parseLegalBlocks
import com.dmb.chantiertracker.presentation.legal.withLegalPlaceholders
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.legal_language_notice
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LegalEnglishVersionTest {

    private val initialLocale: Locale = Locale.getDefault()

    @AfterTest fun restoreLocale() = Locale.setDefault(initialLocale)

    private fun text(locale: Locale, resource: StringResource): String {
        Locale.setDefault(locale)
        return runBlocking { getString(resource) }
    }

    private fun fr(resource: StringResource) = text(Locale.FRENCH, resource)
    private fun en(resource: StringResource) = text(Locale.ENGLISH, resource)

    private fun shape(blocks: List<LegalBlock>) = blocks.map { if (it is LegalBlock.Bullet) "bullet" else "paragraph" }

    private fun tokensOf(text: String) = Regex("\\{[A-Z_]+\\}").findAll(text).map { it.value }.toList().sorted()

    private val titlesIdenticalInBothLanguages = setOf("7. Contact")

    private fun allSections(): List<Pair<LegalDocument, LegalSectionContent>> =
        LegalDocument.entries.flatMap { document -> document.sections.map { document to it } }

    @Test
    fun the_english_resources_are_really_english_not_a_copy_of_the_french() {
        assertEquals("Terms of use", en(LegalDocument.TermsOfUse.label))
        assertEquals("Conditions générales d'utilisation", fr(LegalDocument.TermsOfUse.label))

        allSections().forEach { (document, section) ->
            if (fr(section.title) !in titlesIdenticalInBothLanguages) {
                assertNotEquals(fr(section.title), en(section.title), "title untranslated in $document")
            }
            val frenchBody = fr(section.body)
            if (tokensOf(frenchBody).size == 1 && frenchBody.trim('{', '}', '.').all { it.isUpperCase() || it == '_' }) return@forEach
            assertNotEquals(frenchBody, en(section.body), "body untranslated in $document: $frenchBody")
        }
    }

    @Test
    fun each_document_has_the_same_numbered_sections_as_the_french_one() {
        assertEquals(listOf(7, 10, 10, 10, 4), LegalDocument.entries.map { it.sections.size })

        allSections().forEach { (document, section) ->
            assertEquals(
                fr(section.title).substringBefore('.'),
                en(section.title).substringBefore('.'),
                "section number in $document: ${fr(section.title)}",
            )
        }
    }

    @Test
    fun each_section_has_the_same_paragraph_and_bullet_structure_as_the_french_one() {
        allSections().forEach { (document, section) ->
            assertEquals(
                shape(parseLegalBlocks(fr(section.body))),
                shape(parseLegalBlocks(en(section.body))),
                "block structure in $document: ${fr(section.title)}",
            )
        }
    }

    @Test
    fun each_section_cites_exactly_the_same_placeholders_as_the_french_one() {
        allSections().forEach { (document, section) ->
            assertEquals(
                tokensOf(fr(section.body)),
                tokensOf(en(section.body)),
                "placeholder tokens in $document: ${fr(section.title)}",
            )
        }
    }

    @Test
    fun the_same_fifteen_pieces_of_information_are_missing_in_both_languages() {
        assertEquals(15, LegalPlaceholder.entries.size)

        LegalPlaceholder.entries.forEach { placeholder ->
            assertTrue(fr(placeholder.value).startsWith("[À COMPLÉTER : "), "French marker of ${placeholder.token}")
            assertTrue(en(placeholder.value).startsWith("[TO BE COMPLETED: "), "English marker of ${placeholder.token}")
        }
    }

    @Test
    fun the_shown_english_text_never_contains_a_french_marker_or_an_unresolved_token() {
        Locale.setDefault(Locale.ENGLISH)
        val values = LegalPlaceholder.entries.associate { it.token to runBlocking { getString(it.value) } }

        allSections().forEach { (document, section) ->
            val shown = runBlocking { getString(section.body) }.withLegalPlaceholders(values)
            assertTrue("À COMPLÉTER" !in shown, "French marker in $document: $shown")
            assertTrue('{' !in shown && '}' !in shown, "unresolved token in $document: $shown")
        }
    }

    @Test
    fun the_language_precedence_notice_says_that_only_the_french_version_is_binding_in_both_languages() {
        assertTrue("only the French version is legally binding" in en(Res.string.legal_language_notice))
        assertTrue("seule la version française fait foi juridiquement" in fr(Res.string.legal_language_notice))
    }
}
