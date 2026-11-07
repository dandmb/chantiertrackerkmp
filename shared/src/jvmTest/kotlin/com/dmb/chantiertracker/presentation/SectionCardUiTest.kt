package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.theme.AppTheme
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
                    tone = SectionTone.Orange,
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

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return (hi / lo).toDouble()
    }

    @Test
    fun every_band_keeps_its_colour_and_a_dark_text_in_both_modes() {
        val expected = mapOf(
            SectionTone.Orange to (Color(0xFFE8743B) to Color(0xFF2A1A0E)),
            SectionTone.Lemon to (Color(0xFFB6D45A) to Color(0xFF22300D)),
            SectionTone.Salmon to (Color(0xFFF2A182) to Color(0xFF22300D)),
            SectionTone.Lilac to (Color(0xFFD9C8F5) to Color(0xFF22300D)),
        )
        listOf("light" to LightSectionColors, "dark" to DarkSectionColors).forEach { (mode, c) ->
            expected.forEach { (tone, colours) ->
                assertEquals(colours, c.band(tone).fill to c.band(tone).content, "$mode $tone")
                assertTrue(ratio(c.band(tone).content, c.band(tone).fill) >= 4.5, "$mode $tone text")
                assertTrue(ratio(c.band(tone).actionIcon, c.band(tone).actionFill) >= 3.0, "$mode $tone action")
            }
        }
    }

    @Test
    fun every_tone_is_used_by_a_screen_and_the_photos_band_is_salmon() {
        val presentation = java.io.File(System.getProperty("user.dir"), "src/commonMain/kotlin/com/dmb/chantiertracker/presentation")
        val screens = presentation.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "SectionCard.kt" && it.name != "SectionColors.kt" }
            .map { it.readText() }
            .toList()
        SectionTone.entries.filter { it != SectionTone.Neutral }.forEach { tone ->
            assertTrue(screens.any { "SectionTone.${tone.name}" in it }, "SectionTone.${tone.name} is used by no screen")
        }
        val dailyLog = java.io.File(presentation, "logs/DailyLogScreen.kt").readText()
        assertTrue(
            Regex("""title = stringResource\(Res\.string\.attachments_title\),\s*tone = SectionTone\.Salmon""").containsMatchIn(dailyLog),
            "the photos and attachments of the day sit under a salmon band",
        )
    }

    @Test
    fun a_neutral_card_is_white_with_a_sand_rule_by_day_and_olive_night_with_its_rule_at_night() {
        assertEquals(Color(0xFFFFFFFF), LightSectionColors.card)
        assertEquals(Color(0xFFE6DCC0), LightSectionColors.cardBorder)
        assertEquals(Color(0xFFFFFFFF), LightSectionColors.band(SectionTone.Neutral).fill)
        assertEquals(Color(0xFF20280F), DarkSectionColors.card)
        assertEquals(Color(0xFF34401F), DarkSectionColors.cardBorder)
        assertEquals(Color(0xFF20280F), DarkSectionColors.band(SectionTone.Neutral).fill)
    }

    @Test
    fun the_creation_button_is_orange_with_dark_text_and_the_main_button_ink() {
        assertEquals(Color(0xFFE8743B), LightSectionColors.createButton)
        assertEquals(Color(0xFF2A1A0E), LightSectionColors.onCreateButton)
        assertEquals(Color(0xFFE8743B), DarkSectionColors.createButton)
        assertEquals(Color(0xFF2A1A0E), DarkSectionColors.onCreateButton)
        assertEquals(Color(0xFF22300D), LightColors.primary)
        assertEquals(Color(0xFFE3F0B8), LightColors.onPrimary)
    }

    @Test
    fun the_coloured_band_and_the_white_body_are_painted() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.width(300.dp)) {
                        SectionCard(title = "Achats", tone = SectionTone.Orange, modifier = Modifier.testTag("card")) {
                            DetailEmptyHint("Aucun achat.")
                        }
                    }
                }
            }
        }
        val pixels = onNodeWithTag("card").captureToImage().toPixelMap()
        assertEquals(Color(0xFFE8743B), pixels[150, 4], "orange band")
        assertEquals(Color(0xFFFFFFFF), pixels[150, pixels.height - 4], "white body")
    }

    @Test
    fun the_creation_button_is_painted_orange() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    SectionPrimaryButton("Nouveau projet", {}, Modifier.testTag("create"))
                }
            }
        }
        val pixels = onNodeWithTag("create").captureToImage().toPixelMap()
        assertEquals(Color(0xFFE8743B), pixels[pixels.width / 2, 3], "orange fill")
    }

    @Test
    fun text_reaches_4_5_to_1_in_both_modes() {
        listOf("light" to LightSectionColors, "dark" to DarkSectionColors).forEach { (mode, c) ->
            val text = listOf(
                "title on neutral band" to ratio(c.band(SectionTone.Neutral).content, c.band(SectionTone.Neutral).fill),
                "subtitle on neutral band" to ratio(c.bandSubtitle, c.band(SectionTone.Neutral).fill),
                "creation button" to ratio(c.onCreateButton, c.createButton),
                "secondary button" to ratio(c.onSecondaryButton, c.card),
                "subheading" to ratio(c.subheading, c.card),
                "empty text" to ratio(c.emptyText, c.card),
            )
            text.forEach { (what, r) -> assertTrue(r >= 4.5, "$mode: $what = $r") }
            assertTrue(ratio(c.secondaryButtonBorder, c.card) >= 3.0, "$mode: secondary button border")
            assertTrue(ratio(c.dashedBorder, c.card) >= 3.0, "$mode: dashed frame")
        }
    }
}
