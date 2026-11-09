package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NavigationTransitionsTest {

    @Test
    fun screens_slide_and_fade_in_about_a_quarter_of_a_second() {
        val transitions = NavigationTransitions.of(reduceMotion = false)
        assertEquals(250, transitions.durationMillis)
        assertNotEquals(EnterTransition.None, transitions.enter)
        assertNotEquals(ExitTransition.None, transitions.exit)
        assertNotEquals(EnterTransition.None, transitions.popEnter)
        assertNotEquals(ExitTransition.None, transitions.popExit)
    }

    @Test
    fun the_reduce_animations_setting_of_the_phone_removes_them() {
        val transitions = NavigationTransitions.of(reduceMotion = true)
        assertEquals(0, transitions.durationMillis)
    }
}
