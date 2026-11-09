package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.navigation.ReduceMotionSetting
import com.dmb.chantiertracker.presentation.navigation.rememberReduceMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReduceMotionLiveUiTest {

    private class PhoneSetting(var on: Boolean) : ReduceMotionSetting {
        var watchers = 0
            private set
        private var onChange: (() -> Unit)? = null

        override fun isOn(): Boolean = on

        override fun watch(onChange: () -> Unit): () -> Unit {
            watchers++
            this.onChange = onChange
            return { watchers--; this.onChange = null }
        }

        fun change(value: Boolean) {
            on = value
            onChange?.invoke()
        }
    }

    @Test
    fun changing_the_phone_setting_while_the_app_is_open_applies_at_once() = runComposeUiTest {
        val setting = PhoneSetting(on = false)
        var reduceMotion: Boolean? = null
        setContent { reduceMotion = rememberReduceMotion(setting) }
        waitForIdle()
        assertEquals(false, reduceMotion)

        runOnIdle { setting.change(true) }
        waitForIdle()
        assertTrue(reduceMotion!!)

        runOnIdle { setting.change(false) }
        waitForIdle()
        assertFalse(reduceMotion!!)
    }

    @Test
    fun the_setting_stops_being_watched_when_the_screen_goes_away() = runComposeUiTest {
        val setting = PhoneSetting(on = true)
        var shown by androidx.compose.runtime.mutableStateOf(true)
        setContent { if (shown) rememberReduceMotion(setting) }
        waitForIdle()
        assertEquals(1, setting.watchers)

        shown = false
        waitForIdle()
        assertEquals(0, setting.watchers)
    }
}
