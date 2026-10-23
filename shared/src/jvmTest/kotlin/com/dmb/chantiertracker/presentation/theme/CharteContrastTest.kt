package com.dmb.chantiertracker.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class CharteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun ColorScheme.textPairs(): Map<String, Pair<Color, Color>> = mapOf(
        "onPrimary / primary" to (onPrimary to primary),
        "onPrimaryContainer / primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondaryContainer / secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onBackground / background" to (onBackground to background),
        "onSurface / surface" to (onSurface to surface),
        "onSurfaceVariant / surface" to (onSurfaceVariant to surface),
        "onSurfaceVariant / surfaceVariant" to (onSurfaceVariant to surfaceVariant),
        "primary text / surface" to (primary to surface),
        "error / surface" to (error to surface),
        "onError / error" to (onError to error),
        "onErrorContainer / errorContainer" to (onErrorContainer to errorContainer),
        "tertiary / surface" to (tertiary to surface),
        "onTertiaryContainer / tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "inverseOnSurface / inverseSurface" to (inverseOnSurface to inverseSurface),
    )

    private fun assertAa(scheme: ColorScheme, name: String) {
        scheme.textPairs().forEach { (pair, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue(ratio >= 4.5, "$name — $pair: ${"%.2f".format(ratio)}:1 is below WCAG AA (4.5:1)")
        }
    }

    @Test
    fun every_text_colour_of_the_light_theme_meets_wcag_aa() = assertAa(LightColors, "clair")

    @Test
    fun every_text_colour_of_the_dark_theme_meets_wcag_aa() = assertAa(DarkColors, "sombre")

    @Test
    fun outlines_and_the_warning_banner_are_readable() {
        assertTrue(contrast(LightColors.outline, LightColors.surface) >= 3.0, "light outline (WCAG 1.4.11)")
        assertTrue(contrast(DarkColors.outline, DarkColors.surface) >= 3.0, "dark outline (WCAG 1.4.11)")
        assertTrue(contrast(Color.White, WarningAmber) >= 4.5, "white text on the warning banner")
        assertTrue(contrast(WarningOnAmberLight, WarningAmberContainerLight) >= 4.5, "suspended badge, light")
        assertTrue(contrast(WarningOnAmberDark, WarningAmberContainerDark) >= 4.5, "suspended badge, dark")
    }

    @Test
    fun nothing_of_the_old_terracotta_remains_in_either_theme() {
        val terracotta = listOf(0xFF8B4A3B, 0xFF6B3529, 0xFFA8654F, 0xFFF0DED8, 0xFF5A342A).map { Color(it) }
        listOf(LightColors, DarkColors).forEach { scheme ->
            val used = listOf(
                scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer, scheme.secondary, scheme.secondaryContainer,
                scheme.onSecondaryContainer, scheme.surface, scheme.background, scheme.outline, scheme.inversePrimary,
            )
            assertTrue(used.none { it in terracotta }, "$used")
        }
    }
}
