package com.dmb.chantiertracker.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dmb.chantiertracker.presentation.SectionTone

@Immutable
data class BandColors(
    val fill: Color,
    val content: Color,
    val actionFill: Color,
    val actionIcon: Color,
)

@Immutable
data class SectionColors(
    val card: Color,
    val cardBorder: Color,
    val neutralBand: BandColors,
    val bandSubtitle: Color,
    val createButton: Color,
    val onCreateButton: Color,
    val secondaryButtonBorder: Color,
    val onSecondaryButton: Color,
    val subheading: Color,
    val emptyText: Color,
    val dashedBorder: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val chip: Color,
    val onChip: Color,
) {
    fun band(tone: SectionTone): BandColors = when (tone) {
        SectionTone.Neutral -> neutralBand
        SectionTone.Orange -> OrangeBand
        SectionTone.Lemon -> LemonBand
        SectionTone.Salmon -> SalmonBand
        SectionTone.Lilac -> LilacBand
    }
}

private val OrangeBand = BandColors(fill = Orange, content = OnOrange, actionFill = OnOrange, actionIcon = Orange)
private val LemonBand = BandColors(fill = Lemon, content = Ink, actionFill = Ink, actionIcon = Lemon)
private val SalmonBand = BandColors(fill = Salmon, content = Ink, actionFill = Ink, actionIcon = Salmon)
private val LilacBand = BandColors(fill = Lilac, content = Ink, actionFill = Ink, actionIcon = Lilac)

internal val LightSectionColors = SectionColors(
    card = CardWhite,
    cardBorder = Sand,
    neutralBand = BandColors(fill = CardWhite, content = Ink, actionFill = Ink, actionIcon = CardWhite),
    bandSubtitle = InkSoft,
    createButton = Orange,
    onCreateButton = OnOrange,
    secondaryButtonBorder = Ink,
    onSecondaryButton = Ink,
    subheading = Ink,
    emptyText = InkSoft,
    dashedBorder = InkSoft,
    progressTrack = LemonPale,
    progressFill = Olive,
    chip = LemonPale,
    onChip = Ink,
)

internal val DarkSectionColors = SectionColors(
    card = NightCard,
    cardBorder = NightRule,
    neutralBand = BandColors(fill = NightCard, content = NightText, actionFill = Lemon, actionIcon = Ink),
    bandSubtitle = NightSoft,
    createButton = Orange,
    onCreateButton = OnOrange,
    secondaryButtonBorder = NightText,
    onSecondaryButton = NightText,
    subheading = NightText,
    emptyText = NightSoft,
    dashedBorder = NightSoft,
    progressTrack = NightRule,
    progressFill = Lemon,
    chip = NightChip,
    onChip = NightText,
)

val LocalSectionColors = staticCompositionLocalOf { LightSectionColors }
