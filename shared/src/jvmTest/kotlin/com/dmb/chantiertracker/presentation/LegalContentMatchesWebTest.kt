package com.dmb.chantiertracker.presentation

import com.dmb.chantiertracker.presentation.legal.LegalBlock
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.legal.LegalPlaceholder
import com.dmb.chantiertracker.presentation.legal.parseLegalBlocks
import com.dmb.chantiertracker.presentation.legal.withLegalPlaceholders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalContentMatchesWebTest {

    private val webKeyByDocument = mapOf(
        LegalDocument.LegalNotice to "mentions",
        LegalDocument.TermsOfUse to "cgu",
        LegalDocument.TermsOfSale to "cgv",
        LegalDocument.PrivacyPolicy to "confidentialite",
        LegalDocument.CookiePolicy to "cookies",
    )

    private fun webSnapshot(resource: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/legal/$resource")!!.readText()).jsonObject

    private fun JsonArray.toBlocks(): List<LegalBlock> = flatMap { block ->
        val obj = block.jsonObject
        if (obj.getValue("type").jsonPrimitive.content == "paragraph") {
            listOf(LegalBlock.Paragraph(obj.getValue("text").jsonPrimitive.content))
        } else {
            obj.getValue("items").jsonArray.map { LegalBlock.Bullet(it.jsonPrimitive.content) }
        }
    }

    private fun mobileBlocks(section: com.dmb.chantiertracker.presentation.legal.LegalSectionContent, resolve: Boolean): List<LegalBlock> {
        val body = runBlocking { getString(section.body) }
        val text = if (resolve) body.withLegalPlaceholders(placeholderValues()) else body
        return parseLegalBlocks(text)
    }

    private fun placeholderValues(): Map<String, String> =
        LegalPlaceholder.entries.associate { it.token to runBlocking { getString(it.value) } }

    @Test
    fun the_five_documents_have_exactly_the_sections_the_web_pages_have() {
        val web = webSnapshot("web-legal-tokens.json")

        LegalDocument.entries.forEach { document ->
            val webSections = web.getValue(webKeyByDocument.getValue(document)).jsonObject.getValue("sections").jsonArray
            assertEquals(webSections.size, document.sections.size, "section count of $document")
        }
        assertEquals(listOf(7, 10, 10, 10, 4), LegalDocument.entries.map { it.sections.size })
    }

    @Test
    fun every_section_title_is_identical_to_the_web() {
        val web = webSnapshot("web-legal-tokens.json")

        LegalDocument.entries.forEach { document ->
            val webSections = web.getValue(webKeyByDocument.getValue(document)).jsonObject.getValue("sections").jsonArray
            document.sections.zip(webSections).forEach { (section, webSection) ->
                assertEquals(
                    webSection.jsonObject.getValue("title").jsonPrimitive.content,
                    runBlocking { getString(section.title) },
                    "title in $document",
                )
            }
        }
    }

    @Test
    fun every_paragraph_and_bullet_is_identical_to_the_web_with_placeholders_kept_as_tokens() {
        val web = webSnapshot("web-legal-tokens.json")

        LegalDocument.entries.forEach { document ->
            val webSections = web.getValue(webKeyByDocument.getValue(document)).jsonObject.getValue("sections").jsonArray
            document.sections.zip(webSections).forEachIndexed { index, (section, webSection) ->
                assertEquals(
                    webSection.jsonObject.getValue("blocks").jsonArray.toBlocks(),
                    mobileBlocks(section, resolve = false),
                    "body of section ${index + 1} in $document",
                )
            }
        }
    }

    @Test
    fun the_text_actually_shown_matches_what_the_web_renders_including_the_a_completer_markers() {
        val web = webSnapshot("web-legal-rendered.json")

        LegalDocument.entries.forEach { document ->
            val webSections = web.getValue(webKeyByDocument.getValue(document)).jsonObject.getValue("sections").jsonArray
            document.sections.zip(webSections).forEachIndexed { index, (section, webSection) ->
                assertEquals(
                    webSection.jsonObject.getValue("blocks").jsonArray.toBlocks(),
                    mobileBlocks(section, resolve = true),
                    "rendered section ${index + 1} in $document",
                )
            }
        }
    }

    @Test
    fun the_last_updated_date_placeholder_matches_the_web() {
        val web = webSnapshot("web-legal-rendered.json")

        assertEquals(
            web.getValue("cgu").jsonObject.getValue("lastUpdated").jsonPrimitive.content,
            runBlocking { getString(LegalPlaceholder.LastUpdatedDate.value) },
        )
    }

    @Test
    fun no_placeholder_token_is_left_unresolved_in_any_document() {
        LegalDocument.entries.forEach { document ->
            document.sections.forEach { section ->
                val shown = runBlocking { getString(section.body) }.withLegalPlaceholders(placeholderValues())
                assertTrue('{' !in shown && '}' !in shown, "unresolved token in: $shown")
            }
        }
    }
}
