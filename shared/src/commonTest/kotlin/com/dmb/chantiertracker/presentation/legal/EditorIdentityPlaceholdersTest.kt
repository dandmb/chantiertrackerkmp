package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.support.filledEditorIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorIdentityPlaceholdersTest {

    private val covered = setOf(
        LegalPlaceholder.EditorName, LegalPlaceholder.EditorLegalStatus, LegalPlaceholder.EditorSiret,
        LegalPlaceholder.EditorAddress, LegalPlaceholder.EditorContactEmail, LegalPlaceholder.EditorVatNumber,
        LegalPlaceholder.PublicationDirectorName, LegalPlaceholder.BackendHostDetails,
    )

    @Test
    fun a_filled_identity_supplies_the_eight_tokens_it_covers() {
        val i = filledEditorIdentity
        assertEquals("Chantier Martin SAS", i.valueFor(LegalPlaceholder.EditorName))
        assertEquals("SAS au capital de 1 000 €", i.valueFor(LegalPlaceholder.EditorLegalStatus))
        assertEquals("123 456 789 00012", i.valueFor(LegalPlaceholder.EditorSiret))
        assertEquals("12 rue des Lilas, 30000 Nîmes", i.valueFor(LegalPlaceholder.EditorAddress))
        assertEquals("contact@chantiertracker.com", i.valueFor(LegalPlaceholder.EditorContactEmail))
        assertEquals("FR12123456789", i.valueFor(LegalPlaceholder.EditorVatNumber))
        assertEquals("Jean Martin", i.valueFor(LegalPlaceholder.PublicationDirectorName))
        assertEquals(
            "Hetzner Online GmbH, Industriestr. 25, 91710 Gunzenhausen, Allemagne",
            i.valueFor(LegalPlaceholder.BackendHostDetails),
        )
    }

    // Decision ADR-68: no backend field for these — they keep their fixed resource.
    @Test
    fun the_seven_tokens_without_a_backend_field_always_keep_their_resource() {
        LegalPlaceholder.entries.filter { it !in covered }.forEach { placeholder ->
            assertNull(filledEditorIdentity.valueFor(placeholder), "$placeholder")
        }
        assertEquals(7, LegalPlaceholder.entries.size - covered.size)
    }

    @Test
    fun an_empty_or_unknown_identity_keeps_every_resource() {
        LegalPlaceholder.entries.forEach { placeholder ->
            assertNull(EditorIdentity().valueFor(placeholder), "$placeholder, nothing filled in")
            assertNull((null as EditorIdentity?).valueFor(placeholder), "$placeholder, never fetched")
        }
    }

    @Test
    fun a_blank_value_falls_back_to_the_waiting_text() {
        assertNull(EditorIdentity(siret = "  ").valueFor(LegalPlaceholder.EditorSiret))
    }
}
