package com.dmb.chantiertracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorIdentityTest {

    @Test
    fun the_company_name_wins_once_it_exists() {
        assertEquals("Chantier Martin SAS", EditorIdentity(firstName = "Jean", lastName = "Martin", companyName = "Chantier Martin SAS").editorName())
    }

    @Test
    fun without_a_company_the_editor_is_the_person() {
        assertEquals("Jean Martin", EditorIdentity(firstName = "Jean", lastName = "Martin").editorName())
    }

    @Test
    fun a_single_known_half_of_the_name_is_shown_alone() {
        assertEquals("Martin", EditorIdentity(lastName = "Martin").editorName())
        assertEquals("Jean", EditorIdentity(firstName = "Jean").personName())
    }

    @Test
    fun nothing_known_means_no_editor_name() {
        assertNull(EditorIdentity().editorName())
        assertNull(EditorIdentity().publicationDirectorName())
        assertNull(EditorIdentity().hostingProviderDetails())
    }

    // The publication director is the person, even once a company name exists.
    @Test
    fun the_publication_director_is_derived_from_first_and_last_name() {
        assertEquals("Jean Martin", EditorIdentity(firstName = "Jean", lastName = "Martin", companyName = "Chantier Martin SAS").publicationDirectorName())
        assertNull(EditorIdentity(companyName = "Chantier Martin SAS").publicationDirectorName())
    }

    @Test
    fun the_hosting_provider_shows_whatever_is_known() {
        assertEquals("Hetzner Online GmbH, Industriestr. 25", EditorIdentity(hostingProviderName = "Hetzner Online GmbH", hostingProviderAddress = "Industriestr. 25").hostingProviderDetails())
        assertEquals("Hetzner Online GmbH", EditorIdentity(hostingProviderName = "Hetzner Online GmbH").hostingProviderDetails())
        assertEquals("Industriestr. 25", EditorIdentity(hostingProviderAddress = "Industriestr. 25").hostingProviderDetails())
    }

    @Test
    fun blank_values_count_as_missing_and_are_trimmed() {
        assertEquals("Jean Martin", EditorIdentity(firstName = "  Jean ", lastName = " Martin", companyName = "   ").editorName())
        assertNull(EditorIdentity(firstName = "", lastName = " ").personName())
    }
}
