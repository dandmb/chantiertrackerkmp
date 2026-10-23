package com.dmb.chantiertracker.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.big_shoulders_display_black
import com.dmb.chantiertracker.resources.familjen_grotesk_bold
import com.dmb.chantiertracker.resources.familjen_grotesk_medium
import org.jetbrains.compose.resources.Font

// Light: the web's charte — black ink on white, yellow only as a fill (selected navigation
// item, highlighted surfaces), black pills with white text, thick black outlines.
internal val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Yellow,
    onPrimaryContainer = Ink,
    inversePrimary = Yellow,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Yellow,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Muted,
    surfaceTint = Paper,
    surfaceBright = Paper,
    surfaceDim = PaperDim,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = PaperDim,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    outline = Ink,
    outlineVariant = Rule,
    tertiary = SuccessGreen,
    onTertiary = Paper,
    tertiaryContainer = SuccessGreenLight,
    onTertiaryContainer = SuccessOnGreenLight,
    error = ErrorRed,
    onError = Paper,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRedDark,
    scrim = Ink,
)

// Dark: the same charte at night — white outlines on near-black, yellow pills with black text.
internal val DarkColors = darkColorScheme(
    primary = Yellow,
    onPrimary = Ink,
    primaryContainer = Yellow,
    onPrimaryContainer = Ink,
    inversePrimary = Ink,
    secondary = Paper,
    onSecondary = Ink,
    secondaryContainer = Yellow,
    onSecondaryContainer = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightMuted,
    surfaceTint = Ink,
    surfaceBright = NightSurfaceHigh,
    surfaceDim = Ink,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHigh,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    outline = Paper,
    outlineVariant = NightRule,
    tertiary = SuccessGreenNight,
    onTertiary = Ink,
    tertiaryContainer = SuccessGreenDark,
    onTertiaryContainer = SuccessOnGreenDark,
    error = ErrorRedNight,
    onError = Ink,
    errorContainer = ErrorContainerNight,
    onErrorContainer = OnErrorContainerNight,
    scrim = Ink,
)

// Pills for buttons and chips (M3 buttons already use the full corner), 4 dp like the web
// for fields and cards.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

// Titles: Big Shoulders Display 900, the web's line height of 1.18 (accents clear the comma of
// the line above). Compose has no text-transform: shared title components upper-case the text.
const val DISPLAY_LINE_HEIGHT = 1.18f

@Composable
private fun displayFontFamily(): FontFamily = FontFamily(
    Font(Res.font.big_shoulders_display_black, FontWeight.Black),
)

// Text: Familjen Grotesk, loaded in 500 and 700 only — regular text is 500, anything
// emphasised (semi-bold included) resolves to 700, as on the web.
@Composable
private fun textFontFamily(): FontFamily = FontFamily(
    Font(Res.font.familjen_grotesk_medium, FontWeight.Normal),
    Font(Res.font.familjen_grotesk_medium, FontWeight.Medium),
    Font(Res.font.familjen_grotesk_bold, FontWeight.SemiBold),
    Font(Res.font.familjen_grotesk_bold, FontWeight.Bold),
)

private fun TextStyle.asDisplay(family: FontFamily, size: Int): TextStyle =
    copy(fontFamily = family, fontWeight = FontWeight.Black, fontSize = size.sp, lineHeight = (size * DISPLAY_LINE_HEIGHT).sp, letterSpacing = 0.sp)

@Composable
private fun appTypography(): Typography {
    val display = displayFontFamily()
    val text = textFontFamily()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.asDisplay(display, 57),
        displayMedium = d.displayMedium.asDisplay(display, 45),
        displaySmall = d.displaySmall.asDisplay(display, 36),
        headlineLarge = d.headlineLarge.asDisplay(display, 36),
        headlineMedium = d.headlineMedium.asDisplay(display, 32),
        headlineSmall = d.headlineSmall.asDisplay(display, 28),
        titleLarge = d.titleLarge.asDisplay(display, 24),
        titleMedium = d.titleMedium.copy(fontFamily = text, fontWeight = FontWeight.Bold),
        titleSmall = d.titleSmall.copy(fontFamily = text, fontWeight = FontWeight.Bold),
        bodyLarge = d.bodyLarge.copy(fontFamily = text, fontWeight = FontWeight.Medium),
        bodyMedium = d.bodyMedium.copy(fontFamily = text, fontWeight = FontWeight.Medium),
        bodySmall = d.bodySmall.copy(fontFamily = text, fontWeight = FontWeight.Medium),
        labelLarge = d.labelLarge.copy(fontFamily = text, fontWeight = FontWeight.Bold),
        labelMedium = d.labelMedium.copy(fontFamily = text, fontWeight = FontWeight.Bold),
        labelSmall = d.labelSmall.copy(fontFamily = text, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = appTypography(),
        content = content,
    )
}
