package com.dmb.chantiertracker.presentation.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemBarIconsTest {

    @Test
    fun the_splash_on_its_ink_background_gets_light_icons_even_in_light_mode() {
        assertTrue(systemBarIconsAreLight(showingSplash = true, background = LightColors.background))
        assertTrue(systemBarIconsAreLight(showingSplash = true, background = DarkColors.background))
    }

    @Test
    fun the_screens_get_icons_that_contrast_with_the_app_theme_not_the_phone_theme() {
        assertFalse(systemBarIconsAreLight(showingSplash = false, background = LightColors.background))
        assertTrue(systemBarIconsAreLight(showingSplash = false, background = DarkColors.background))
    }
}
