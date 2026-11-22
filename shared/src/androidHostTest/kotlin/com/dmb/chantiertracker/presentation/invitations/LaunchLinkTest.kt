package com.dmb.chantiertracker.presentation.invitations

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchLinkTest {

    @Test
    fun a_link_that_launches_the_app_is_new() {
        assertTrue(launchLinkIsNew(stateRestored = false, intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun the_link_replayed_by_the_recent_apps_after_the_process_died_is_not_new() {
        assertFalse(launchLinkIsNew(stateRestored = false, intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }

    @Test
    fun the_link_kept_by_an_activity_recreated_from_saved_state_is_not_new() {
        assertFalse(launchLinkIsNew(stateRestored = true, intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
