package com.dmb.chantiertracker.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val OffWhite = Color(0xFFEDEDED)

@Immutable
data class SectionColors(
    val card: Color,
    val cardBorder: Color,
    val yellowBand: Color,
    val onYellowBand: Color,
    val yellowBandSubtitle: Color,
    val inkBand: Color,
    val onInkBand: Color,
    val inkBandIcon: Color,
    val inkBandSubtitle: Color,
    val bandRule: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val secondaryButtonBorder: Color,
    val onSecondaryButton: Color,
    val subheading: Color,
    val emptyText: Color,
    val dashedBorder: Color,
)

internal val LightSectionColors = SectionColors(
    card = Paper,
    cardBorder = Ink,
    yellowBand = Yellow,
    onYellowBand = Ink,
    yellowBandSubtitle = InkHover,
    inkBand = Ink,
    onInkBand = Paper,
    inkBandIcon = Yellow,
    inkBandSubtitle = NightMuted,
    bandRule = Ink,
    primaryButton = Ink,
    onPrimaryButton = Paper,
    secondaryButtonBorder = Ink,
    onSecondaryButton = Ink,
    subheading = Ink,
    emptyText = Muted,
    dashedBorder = Muted,
)

internal val DarkSectionColors = SectionColors(
    card = NightSurface,
    cardBorder = OffWhite,
    yellowBand = Yellow,
    onYellowBand = Ink,
    yellowBandSubtitle = InkHover,
    inkBand = InkHover,
    onInkBand = Paper,
    inkBandIcon = Yellow,
    inkBandSubtitle = NightMuted,
    bandRule = OffWhite,
    primaryButton = OffWhite,
    onPrimaryButton = Ink,
    secondaryButtonBorder = OffWhite,
    onSecondaryButton = Paper,
    subheading = Paper,
    emptyText = NightMuted,
    dashedBorder = NightMuted,
)

val LocalSectionColors = staticCompositionLocalOf { LightSectionColors }
