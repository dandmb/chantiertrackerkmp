package com.dmb.chantiertracker.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.plus_jakarta_medium
import com.dmb.chantiertracker.resources.plus_jakarta_regular
import com.dmb.chantiertracker.resources.plus_jakarta_semibold
import org.jetbrains.compose.resources.Font

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaDark,
    secondary = WarmGray,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = TerracottaDark,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = DividerLight,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = SuccessGreenLight,
    onTertiaryContainer = SuccessOnGreenLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = TerracottaLight,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = TerracottaContainer,
    secondary = WarmGrayLight,
    onSecondary = Color.Black,
    secondaryContainer = TerracottaContainerDark,
    onSecondaryContainer = TerracottaContainer,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = DividerDark,
    tertiary = SuccessGreenLight,
    onTertiary = Color.Black,
    tertiaryContainer = SuccessGreenDark,
    onTertiaryContainer = SuccessOnGreenDark,
    error = ErrorRed,
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
)

@Composable
private fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_semibold, FontWeight.SemiBold),
)

@Composable
private fun appTypography(): Typography {
    val family = appFontFamily()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineMedium = d.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineSmall = d.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleLarge = d.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        titleSmall = d.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodyLarge = d.bodyLarge.copy(fontFamily = family),
        bodyMedium = d.bodyMedium.copy(fontFamily = family),
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelMedium = d.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelSmall = d.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
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
