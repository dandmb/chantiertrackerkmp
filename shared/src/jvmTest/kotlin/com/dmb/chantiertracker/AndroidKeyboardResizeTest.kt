package com.dmb.chantiertracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidKeyboardResizeTest {

    @Test
    fun the_keyboard_resizes_the_screen_instead_of_pushing_it_under_the_status_bar() {
        val manifest = File("../androidApp/src/main/AndroidManifest.xml").readText()
        val activity = Regex("""<activity\b[^>]*android:name="\.MainActivity"[^>]*>""").find(manifest)?.value
            ?: error("MainActivity not declared")
        assertTrue("""android:windowSoftInputMode="adjustResize"""" in activity, activity)
    }
}
