package com.dmb.chantiertracker.presentation.theme

import androidx.compose.runtime.CompositionLocalProvider
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
import com.dmb.chantiertracker.resources.familjen_grotesk_regular
import org.jetbrains.compose.resources.Font

internal val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = LemonPale,
    primaryContainer = LemonPale,
    onPrimaryContainer = Ink,
    inversePrimary = Lemon,
    secondary = Olive,
    onSecondary = Cream,
    secondaryContainer = LemonPale,
    onSecondaryContainer = Ink,
    tertiary = Olive,
    onTertiary = Cream,
    tertiaryContainer = LemonPale,
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = LemonPale,
    onSurfaceVariant = InkSoft,
    surfaceTint = Cream,
    surfaceBright = CardWhite,
    surfaceDim = Sand,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = CardWhite,
    inverseSurface = Ink,
    inverseOnSurface = Cream,
    outline = Ink,
    outlineVariant = Sand,
    error = Brick,
    onError = OnBrick,
    errorContainer = SalmonPale,
    onErrorContainer = Brick,
    scrim = Ink,
)

internal val DarkColors = darkColorScheme(
    primary = Lemon,
    onPrimary = Ink,
    primaryContainer = NightChip,
    onPrimaryContainer = NightText,
    inversePrimary = Olive,
    secondary = Lemon,
    onSecondary = Ink,
    secondaryContainer = NightChip,
    onSecondaryContainer = NightText,
    tertiary = Lemon,
    onTertiary = Ink,
    tertiaryContainer = NightChip,
    onTertiaryContainer = NightText,
    background = Night,
    onBackground = NightText,
    surface = Night,
    onSurface = NightText,
    surfaceVariant = NightChip,
    onSurfaceVariant = NightSoft,
    surfaceTint = Night,
    surfaceBright = NightCard,
    surfaceDim = Night,
    surfaceContainerLowest = NightCard,
    surfaceContainerLow = NightCard,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = NightCard,
    inverseSurface = NightChip,
    inverseOnSurface = NightText,
    outline = NightText,
    outlineVariant = NightRule,
    error = Salmon,
    onError = OnOrange,
    errorContainer = Brick,
    onErrorContainer = OnBrick,
    scrim = Ink,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

const val DISPLAY_LINE_HEIGHT = 1.18f

@Composable
private fun displayFontFamily(): FontFamily = FontFamily(
    Font(Res.font.big_shoulders_display_black, FontWeight.Black),
)

@Composable
private fun textFontFamily(): FontFamily = FontFamily(
    Font(Res.font.familjen_grotesk_regular, FontWeight.Normal),
    Font(Res.font.familjen_grotesk_medium, FontWeight.Medium),
    Font(Res.font.familjen_grotesk_bold, FontWeight.SemiBold),
    Font(Res.font.familjen_grotesk_bold, FontWeight.Bold),
)

private fun TextStyle.asDisplay(family: FontFamily, size: Int, letterSpacing: Float = 0f): TextStyle =
    copy(fontFamily = family, fontWeight = FontWeight.Black, fontSize = size.sp, lineHeight = (size * DISPLAY_LINE_HEIGHT).sp, letterSpacing = letterSpacing.sp)

@Composable
private fun appTypography(): Typography {
    val display = displayFontFamily()
    val text = textFontFamily()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.asDisplay(display, 57),
        displayMedium = d.displayMedium.asDisplay(display, 52),
        displaySmall = d.displaySmall.asDisplay(display, 48),
        headlineLarge = d.headlineLarge.asDisplay(display, 36),
        headlineMedium = d.headlineMedium.asDisplay(display, 32),
        headlineSmall = d.headlineSmall.asDisplay(display, 28),
        titleLarge = d.titleLarge.asDisplay(display, 22, letterSpacing = 0.44f),
        titleMedium = d.titleMedium.copy(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = d.titleSmall.copy(fontFamily = text, fontWeight = FontWeight.Bold),
        bodyLarge = d.bodyLarge.copy(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = d.bodyMedium.copy(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = d.bodySmall.copy(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
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
    ) {
        CompositionLocalProvider(LocalSectionColors provides if (darkTheme) DarkSectionColors else LightSectionColors, content = content)
    }
}
