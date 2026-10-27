package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.presentation.theme.DarkColors
import com.dmb.chantiertracker.presentation.theme.DarkSectionColors
import com.dmb.chantiertracker.presentation.theme.LightColors
import com.dmb.chantiertracker.presentation.theme.LightSectionColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SectionCardUiTest {

    @Test
    fun the_title_is_upper_cased_with_its_subtitle_and_the_action_answers() = runComposeUiTest {
        var edits = 0
        setContent {
            AppTheme(darkTheme = false) {
                SectionCard(
                    title = "Achats",
                    icon = ShoppingCartIcon,
                    tone = SectionTone.Yellow,
                    subtitle = "Aucun résumé.",
                    action = { SectionHeaderAction("Modifier", { edits++ }, icon = EditIcon) },
                ) { DetailEmptyHint("Aucun article pour l'instant.") }
            }
        }

        onNodeWithText("ACHATS", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Aucun résumé.").assertIsDisplayed()
        onNodeWithText("Aucun article pour l'instant.").assertIsDisplayed()
        onNodeWithText("Modifier").performClick()
        assertEquals(1, edits)
    }

    @Test
    fun on_a_narrow_phone_at_double_font_size_the_action_moves_under_the_title_and_nothing_is_cut() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) {
                        SectionCard(
                            title = "Invitations en attente",
                            action = { SectionHeaderAction("Inviter un membre", {}) },
                        ) { DetailEmptyHint("Aucune invitation.") }
                    }
                }
            }
        }

        val title = onNodeWithText("INVITATIONS EN ATTENTE", useUnmergedTree = true).getBoundsInRoot()
        val action = onNodeWithText("Inviter un membre").getBoundsInRoot()
        assertTrue(action.top >= title.bottom, "the action sits under the title: title=$title action=$action")
        assertTrue(action.right <= 320.dp && title.right <= 320.dp, "nothing goes past the screen width")
    }

    @Test
    fun a_short_title_keeps_its_action_on_the_same_line() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.width(412.dp)) {
                    SectionCard(title = "Étapes", action = { SectionHeaderAction("Ajouter", {}) }) {}
                }
            }
        }

        val title = onNodeWithText("ÉTAPES", useUnmergedTree = true).getBoundsInRoot()
        val action = onNodeWithText("Ajouter").getBoundsInRoot()
        assertTrue(action.left >= title.right && action.top < title.bottom, "title=$title action=$action")
    }

    @Test
    fun text_reaches_4_5_to_1_and_borders_and_icons_3_to_1_in_both_modes() {
        fun ratio(a: Color, b: Color): Double {
            val (hi, lo) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
            return (hi / lo).toDouble()
        }
        listOf("light" to (LightSectionColors to LightColors.background), "dark" to (DarkSectionColors to DarkColors.background)).forEach { (mode, pair) ->
            val (c, page) = pair
            val text = listOf(
                "title on yellow" to ratio(c.onYellowBand, c.yellowBand),
                "subtitle on yellow" to ratio(c.yellowBandSubtitle, c.yellowBand),
                "title on ink band" to ratio(c.onInkBand, c.inkBand),
                "subtitle on ink band" to ratio(c.inkBandSubtitle, c.inkBand),
                "primary button" to ratio(c.onPrimaryButton, c.primaryButton),
                "secondary button" to ratio(c.onSecondaryButton, c.card),
                "subheading" to ratio(c.subheading, c.card),
                "empty text" to ratio(c.emptyText, c.card),
            )
            val graphics = listOf(
                "card border on the page" to ratio(c.cardBorder, page),
                "card border on the card" to ratio(c.cardBorder, c.card),
                "icon on ink band" to ratio(c.inkBandIcon, c.inkBand),
                "icon on yellow" to ratio(c.onYellowBand, c.yellowBand),
                "band rule under the ink band" to ratio(c.bandRule, if (mode == "dark") c.inkBand else c.yellowBand),
                "dashed frame" to ratio(c.dashedBorder, c.card),
                "secondary button border" to ratio(c.secondaryButtonBorder, c.card),
            )
            text.forEach { (what, r) -> assertTrue(r >= 4.5, "$mode: $what = $r") }
            graphics.forEach { (what, r) -> assertTrue(r >= 3.0, "$mode: $what = $r") }
        }
    }
}
