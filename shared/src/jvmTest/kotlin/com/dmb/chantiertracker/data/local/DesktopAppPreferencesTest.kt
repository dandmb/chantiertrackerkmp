package com.dmb.chantiertracker.data.local

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAppPreferencesTest {

    private val dir = Files.createTempDirectory("ct-prefs")
    private val file = dir.resolve("preferences.properties")

    @AfterTest fun cleanUp() { dir.toFile().deleteRecursively() }

    @Test
    fun reads_null_before_anything_is_written() {
        assertNull(DesktopAppPreferences(file).read("app_language"))
    }

    @Test
    fun a_written_value_survives_a_fresh_instance() {
        DesktopAppPreferences(file).write("app_language", "French")
        DesktopAppPreferences(file).write("app_theme_mode", "Dark")

        val reopened = DesktopAppPreferences(file)
        assertEquals("French", reopened.read("app_language"))
        assertEquals("Dark", reopened.read("app_theme_mode"))
    }

    @Test
    fun writing_null_clears_the_key_and_leaves_the_others() {
        val prefs = DesktopAppPreferences(file)
        prefs.write("app_language", "English")
        prefs.write("app_theme_mode", "Light")

        prefs.write("app_language", null)

        assertNull(DesktopAppPreferences(file).read("app_language"))
        assertEquals("Light", DesktopAppPreferences(file).read("app_theme_mode"))
    }
}
