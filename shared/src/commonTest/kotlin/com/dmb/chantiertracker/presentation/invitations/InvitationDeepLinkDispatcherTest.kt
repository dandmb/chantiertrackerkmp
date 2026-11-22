package com.dmb.chantiertracker.presentation.invitations

import com.dmb.chantiertracker.support.FakeAppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvitationDeepLinkDispatcherTest {

    private val preferences = FakeAppPreferences()

    @Test
    fun the_redirect_of_a_link_is_claimed_only_once() {
        val links = InvitationDeepLinkDispatcher(preferences)
        links.dispatch("tok")

        assertTrue(links.claimLinkOpening("tok"))
        assertFalse(links.claimLinkOpening("tok"))
    }

    @Test
    fun opening_the_link_again_allows_a_new_redirect() {
        val links = InvitationDeepLinkDispatcher(preferences)
        links.dispatch("tok")
        links.claimLinkOpening("tok")

        links.dispatch("tok")

        assertTrue(links.claimLinkOpening("tok"))
    }

    @Test
    fun a_token_kept_before_a_restart_comes_back_without_a_redirect() {
        InvitationDeepLinkDispatcher(preferences).dispatch("tok")

        val restarted = InvitationDeepLinkDispatcher(preferences)
        restarted.reopenHeld()

        assertEquals("tok", restarted.pending.value)
        assertFalse(restarted.claimLinkOpening("tok"))
    }

    @Test
    fun forget_drops_the_token_and_its_saved_copy() {
        val links = InvitationDeepLinkDispatcher(preferences)
        links.dispatch("tok")

        links.forget()
        links.reopenHeld()

        assertNull(links.pending.value)
        assertTrue(preferences.store.isEmpty())
        InvitationDeepLinkDispatcher(preferences).apply { reopenHeld() }.also { assertNull(it.pending.value) }
    }

    @Test
    fun a_settled_invitation_is_no_longer_saved() {
        val links = InvitationDeepLinkDispatcher(preferences)
        links.dispatch("tok")
        links.consume()

        links.release("tok")

        assertTrue(preferences.store.isEmpty())
    }

    @Test
    fun releasing_an_older_token_keeps_the_newer_one() {
        val links = InvitationDeepLinkDispatcher(preferences)
        links.dispatch("old")
        links.dispatch("new")

        links.release("old")

        assertEquals("new", links.pending.value)
        assertEquals("new", preferences.store.values.single())
    }
}
