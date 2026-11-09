package com.dmb.chantiertracker.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeCPaletteTest {

    private fun ColorScheme.everyColour(): List<Color> = listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, secondary, onSecondary, secondaryContainer,
        onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, onTertiaryContainer, background, onBackground, surface,
        onSurface, surfaceVariant, onSurfaceVariant, surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer,
        onErrorContainer, outline, outlineVariant, scrim, surfaceBright, surfaceDim, surfaceContainer, surfaceContainerHigh,
        surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest,
    )

    @Test
    fun the_light_theme_is_the_cream_page_with_white_cards_sand_rules_and_dark_olive_ink() {
        assertEquals(Color(0xFFFBF7EA), LightColors.background)
        assertEquals(Color(0xFFFBF7EA), LightColors.surface)
        assertEquals(Color(0xFFFFFFFF), LightColors.surfaceContainerLowest)
        assertEquals(Color(0xFFFFFFFF), LightColors.surfaceContainerHigh)
        assertEquals(Color(0xFFE6DCC0), LightColors.outlineVariant)
        assertEquals(Color(0xFF22300D), LightColors.onBackground)
        assertEquals(Color(0xFF22300D), LightColors.onSurface)
        assertEquals(Color(0xFF5B6640), LightColors.onSurfaceVariant)
        assertEquals(Color(0xFF22300D), LightColors.primary)
        assertEquals(Color(0xFFE3F0B8), LightColors.onPrimary)
        assertEquals(Color(0xFFE3F0B8), LightColors.primaryContainer)
        assertEquals(Color(0xFF3D4D1A), LightColors.secondary)
        assertEquals(Color(0xFFB3361E), LightColors.error)
        assertEquals(Color(0xFF22300D), LightColors.outline)
    }

    @Test
    fun the_dark_theme_is_the_night_olive_page_with_cream_text_and_lemon_accents() {
        assertEquals(Color(0xFF151A0C), DarkColors.background)
        assertEquals(Color(0xFF151A0C), DarkColors.surface)
        assertEquals(Color(0xFF20280F), DarkColors.surfaceContainerLowest)
        assertEquals(Color(0xFF3A4724), DarkColors.surfaceContainerHigh)
        assertEquals(Color(0xFF34401F), DarkColors.outlineVariant)
        assertEquals(Color(0xFFFBF7EA), DarkColors.onBackground)
        assertEquals(Color(0xFFFBF7EA), DarkColors.onSurface)
        assertEquals(Color(0xFFC9D3A8), DarkColors.onSurfaceVariant)
        assertEquals(Color(0xFF2C3618), DarkColors.primaryContainer)
        assertEquals(Color(0xFFB6D45A), DarkColors.primary)
        assertEquals(Color(0xFFB6D45A), DarkColors.secondary)
    }

    @Test
    fun nothing_of_the_black_and_yellow_charte_remains_in_either_theme() {
        val oldCharte = listOf(0xFF0A0A0A, 0xFFFFD400, 0xFFFFFBE0, 0xFF161616, 0xFF222222, 0xFF555555, 0xFFE6E6E6).map { Color(it) }
        listOf("clair" to LightColors, "sombre" to DarkColors).forEach { (name, scheme) ->
            val left = scheme.everyColour().filter { it in oldCharte }
            assertTrue(left.isEmpty(), "$name: $left")
        }
    }
}
