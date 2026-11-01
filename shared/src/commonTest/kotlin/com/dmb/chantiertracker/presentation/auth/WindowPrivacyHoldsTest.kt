package com.dmb.chantiertracker.presentation.auth

import com.dmb.chantiertracker.presentation.auth.components.WindowPrivacyHolds
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowPrivacyHoldsTest {

    @Test
    fun the_window_stays_private_until_the_last_field_is_gone() {
        val changes = mutableListOf<Boolean>()
        val holds = WindowPrivacyHolds(changes::add)

        holds.acquire()
        holds.acquire()
        holds.release()
        assertEquals(listOf(true), changes)

        holds.release()
        assertEquals(listOf(true, false), changes)
    }
}
