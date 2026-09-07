package com.dmb.chantiertracker.presentation.projects.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryDescriptionTest {

    private fun List<HistorySegment>.plain() = joinToString("") { it.text }
    private fun List<HistorySegment>.values() = filter { it.kind == HistorySegmentKind.VALUE }.map { it.text }
    private fun List<HistorySegment>.targets() = filter { it.kind == HistorySegmentKind.TARGET }.map { it.text }

    @Test
    fun the_reassembled_text_always_equals_the_input() {
        val samples = listOf(
            "Dan a créé le projet Villa Vidal",
            "Jean a modifié le budget prévisionnel de l'étape Gros œuvre : 500 000 → 600 000 EUR",
            "Sam (superviseur) a retiré 3 tonnes de Ciment du stock sur l'étape Fondation",
            "Sam a ajouté un achat sur l'étape Gros œuvre : 12 sacs de ciment à 3,5 EUR",
            "Jean Marchand a suspendu le projet Villa Vidal",
            "Une phrase sans rien de particulier à styliser.",
            "",
        )
        for (s in samples) assertEquals(s, parseHistoryDescription(s).plain(), "round-trips: <$s>")
    }

    @Test
    fun a_project_name_at_the_end_is_a_target() {
        val segs = parseHistoryDescription("Dan a créé le projet Villa Vidal")
        assertEquals(listOf("le projet Villa Vidal"), segs.targets())
        assertEquals("Dan a créé ", segs.first().text)
        assertEquals(HistorySegmentKind.PLAIN, segs.first().kind)
    }

    @Test
    fun a_stage_name_before_a_colon_is_a_target_and_the_amounts_are_values() {
        val segs = parseHistoryDescription(
            "Jean a modifié le budget prévisionnel de l'étape Gros œuvre : 500 000 → 600 000 EUR",
        )
        assertEquals(listOf("l'étape Gros œuvre"), segs.targets())
        assertEquals(listOf("500 000", "600 000 EUR"), segs.values())
    }

    @Test
    fun a_quantity_keeps_its_unit_and_a_trailing_grammar_word_is_dropped() {
        val segs = parseHistoryDescription(
            "Sam (superviseur) a retiré 3 tonnes de Ciment du stock sur l'étape Fondation",
        )
        assertEquals(listOf("3 tonnes"), segs.values(), "the unit stays, ' de' does not")
        assertEquals(listOf("l'étape Fondation"), segs.targets())
    }

    @Test
    fun several_amounts_and_a_stage_in_one_purchase_sentence() {
        val segs = parseHistoryDescription(
            "Sam a ajouté un achat sur l'étape Gros œuvre : 12 sacs de ciment à 3,5 EUR",
        )
        assertEquals(listOf("l'étape Gros œuvre"), segs.targets())
        assertEquals(listOf("12 sacs", "3,5 EUR"), segs.values())
    }

    @Test
    fun a_digit_inside_a_stage_name_is_not_double_styled() {
        val segs = parseHistoryDescription("Jean a ajouté l'étape Fondation 2")
        assertEquals(listOf("l'étape Fondation 2"), segs.targets())
        assertTrue(segs.values().isEmpty(), "the 2 belongs to the name, not a standalone value")
    }

    @Test
    fun an_unrecognised_sentence_stays_entirely_plain() {
        val text = "Quelque chose d'inattendu s'est produit ici."
        val segs = parseHistoryDescription(text)
        assertEquals(1, segs.size)
        assertEquals(HistorySegmentKind.PLAIN, segs.single().kind)
        assertEquals(text, segs.single().text)
    }

    @Test
    fun a_status_change_targets_the_project() {
        val segs = parseHistoryDescription("Jean Marchand a marqué comme terminé le projet Villa Vidal")
        assertEquals(listOf("le projet Villa Vidal"), segs.targets())
    }
}
