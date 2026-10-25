package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.domain.model.LegalLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegalDocumentTest {

    @Test
    fun each_link_asks_the_server_for_its_document_in_the_order_of_the_legal_menu() {
        assertEquals(
            listOf("legal-notice", "terms-of-use", "terms-of-sale", "privacy-policy", "cookie-policy", "account-deletion"),
            LegalDocument.entries.map { it.slug },
        )
    }

    @Test
    fun a_slug_finds_its_document_back_and_an_unknown_one_finds_nothing() {
        LegalDocument.entries.forEach { assertEquals(it, LegalDocument.fromSlug(it.slug)) }
        assertNull(LegalDocument.fromSlug("TERMS_OF_USE"))
    }

    @Test
    fun an_english_phone_reads_the_english_text_and_every_other_one_the_french_text() {
        assertEquals(LegalLanguage.English, LegalLanguage.forLocale("en"))
        assertEquals(LegalLanguage.English, LegalLanguage.forLocale("en_US"))
        assertEquals(LegalLanguage.English, LegalLanguage.forLocale("en-GB"))
        assertEquals(LegalLanguage.French, LegalLanguage.forLocale("fr_FR"))
        assertEquals(LegalLanguage.French, LegalLanguage.forLocale("es_ES"))
        assertEquals(LegalLanguage.French, LegalLanguage.forLocale(""))
    }
}
