package com.dmb.chantiertracker.presentation.projects

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingInvitationMessageTest {

    private val bold = SpanStyle(fontWeight = FontWeight.SemiBold)

    @Test
    fun the_project_name_and_inviter_name_are_the_only_emphasised_spans() {
        val result = formatWithEmphasis(
            template = "Tu as une invitation en attente pour rejoindre %1\$s en tant que %2\$s, envoyée par %3\$s.",
            parts = listOf("Villa Vidal" to true, "Superviseur" to false, "Jean Marchand" to true),
        )

        assertEquals(
            "Tu as une invitation en attente pour rejoindre Villa Vidal en tant que Superviseur, envoyée par Jean Marchand.",
            result.text,
        )
        val emphasised = result.spanStyles.filter { it.item == bold }.map { result.text.substring(it.start, it.end) }
        assertEquals(listOf("Villa Vidal", "Jean Marchand"), emphasised)
    }

    @Test
    fun the_template_without_an_inviter_only_emphasises_the_project_name() {
        val result = formatWithEmphasis(
            template = "You have a pending invitation to join %1\$s as %2\$s.",
            parts = listOf("Villa Vidal" to true, "Supervisor" to false),
        )

        assertEquals("You have a pending invitation to join Villa Vidal as Supervisor.", result.text)
        val emphasised = result.spanStyles.filter { it.item == bold }.map { result.text.substring(it.start, it.end) }
        assertEquals(listOf("Villa Vidal"), emphasised)
    }

    @Test
    fun a_placeholder_with_no_matching_part_becomes_empty_rather_than_crashing() {
        val result = formatWithEmphasis("A %1\$s B %2\$s C", parts = listOf("X" to false))
        assertEquals("A X B  C", result.text)
    }
}
