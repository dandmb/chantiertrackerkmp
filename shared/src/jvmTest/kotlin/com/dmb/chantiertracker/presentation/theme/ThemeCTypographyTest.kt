package com.dmb.chantiertracker.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ThemeCTypographyTest {

    private val repository = File(System.getProperty("user.dir")).parentFile
    private val fonts = File(repository, "shared/src/commonMain/composeResources/font")

    private fun Typography.all(): Map<String, TextStyle> = mapOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
    )

    @Test
    fun both_families_are_embedded_with_familjen_400_for_running_text_and_their_ofl_licences() {
        listOf("big_shoulders_display_black.ttf", "familjen_grotesk_regular.ttf", "familjen_grotesk_medium.ttf", "familjen_grotesk_bold.ttf")
            .forEach { assertTrue(File(fonts, it).length() > 10_000, "missing font $it") }
        val licences = File(repository, "shared/src/commonMain/composeResources/files/licenses")
        listOf("big-shoulders-display-OFL.txt", "familjen-grotesk-OFL.txt").forEach {
            assertTrue(File(licences, it).readText().contains("SIL OPEN FONT LICENSE", ignoreCase = true), it)
        }
    }

    @Test
    fun titles_are_big_shoulders_900_running_text_familjen_400_and_nothing_is_serif_or_italic() = runComposeUiTest {
        var typography: Typography? = null
        setContent { AppTheme(darkTheme = false) { typography = MaterialTheme.typography } }
        waitForIdle()
        val styles = assertNotNull(typography).all()

        styles.forEach { (name, style) ->
            assertNotEquals(FontStyle.Italic, style.fontStyle, "$name is italic")
            assertNotEquals(FontFamily.Serif, style.fontFamily, "$name is serif")
            assertNotNull(style.fontFamily, "$name falls back to the system font")
        }
        listOf("displaySmall", "headlineSmall", "titleLarge").forEach { assertEquals(FontWeight.Black, styles.getValue(it).fontWeight, it) }
        listOf("bodyLarge", "bodyMedium", "bodySmall").forEach { assertEquals(FontWeight.Normal, styles.getValue(it).fontWeight, it) }
        assertEquals(48f, styles.getValue("displaySmall").fontSize.value, "screen title")
        assertEquals(28f, styles.getValue("headlineSmall").fontSize.value, "top bar title")
        assertEquals(22f, styles.getValue("titleLarge").fontSize.value, "section title")
    }
}
