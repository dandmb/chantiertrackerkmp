package com.dmb.chantiertracker.presentation.invitations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvitationDeepLinkTest {

    @Test
    fun extracts_the_token_from_a_real_invitation_link() {
        assertEquals("abc123", parseInvitationToken("https://chantiertracker.com/invitations/abc123"))
    }

    @Test
    fun is_case_insensitive_on_scheme_and_host() {
        assertEquals("abc123", parseInvitationToken("HTTPS://ChantierTracker.COM/invitations/abc123"))
    }

    @Test
    fun tolerates_a_trailing_slash_query_string_or_fragment() {
        assertEquals("abc123", parseInvitationToken("https://chantiertracker.com/invitations/abc123?utm_source=email"))
        assertEquals("abc123", parseInvitationToken("https://chantiertracker.com/invitations/abc123#section"))
    }

    @Test
    fun an_unrecognized_scheme_host_or_path_resolves_to_nothing_rather_than_throwing() {
        assertNull(parseInvitationToken("chantiertracker://invitations/abc123"))
        assertNull(parseInvitationToken("https://example.com/invitations/abc123"))
        assertNull(parseInvitationToken("https://chantiertracker.com/projects/1"))
        assertNull(parseInvitationToken("https://chantiertracker.com/invitations/"))
        assertNull(parseInvitationToken(""))
        assertNull(parseInvitationToken("not a url at all"))
    }
}
