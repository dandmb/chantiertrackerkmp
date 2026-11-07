package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.PhotoCameraIcon
import com.dmb.chantiertracker.presentation.main.VideocamIcon
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ActionStylesUiTest {

    private fun button(text: String) = hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun an_outlined_action_is_a_48_dp_pill_with_a_2_dp_outline_an_icon_and_one_line_of_text() = runComposeUiTest {
        var taps = 0
        setContent {
            AppTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    OutlinedActionButton("Ajouter une photo", AddIcon, onClick = { taps++ }, modifier = Modifier.testTag("action"))
                }
            }
        }

        val bounds = onNode(button("Ajouter une photo")).getBoundsInRoot()
        assertTrue(bounds.height >= 48.dp, "height ${bounds.height}")
        val pixels = onNodeWithTag("action").captureToImage().toPixelMap()
        val ink = Color(0xFF22300D)
        assertEquals(ink, pixels[pixels.width / 2, 0], "2 dp ink outline on top")
        assertEquals(ink, pixels[pixels.width / 2, 1], "2 dp ink outline on top")
        assertEquals(1, layoutOf("Ajouter une photo").lineCount)
        onNode(button("Ajouter une photo")).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun actions_that_do_not_fit_on_one_line_are_stacked_full_width_without_wrapping_their_text() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.width(280.dp)) {
                    AdaptiveActions {
                        OutlinedActionButton("Ajouter une photo", AddIcon, onClick = {})
                        OutlinedActionButton("Prendre une photo", PhotoCameraIcon, onClick = {})
                        OutlinedActionButton("Ajouter une vidéo", VideocamIcon, onClick = {})
                    }
                }
            }
        }

        val first = onNode(button("Ajouter une photo")).getBoundsInRoot()
        val second = onNode(button("Prendre une photo")).getBoundsInRoot()
        val third = onNode(button("Ajouter une vidéo")).getBoundsInRoot()
        assertTrue(second.top >= first.bottom && third.top >= second.bottom, "stacked: $first $second $third")
        listOf(first, second, third).forEach { assertEquals(280.dp, it.width, "full width: $it") }
        listOf("Ajouter une photo", "Prendre une photo", "Ajouter une vidéo").forEach { assertEquals(1, layoutOf(it).lineCount, it) }
    }

    @Test
    fun actions_that_fit_stay_side_by_side() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.width(700.dp)) {
                    AdaptiveActions {
                        OutlinedActionButton("Ajouter une photo", AddIcon, onClick = {})
                        OutlinedActionButton("Ajouter une vidéo", VideocamIcon, onClick = {})
                    }
                }
            }
        }

        val first = onNode(button("Ajouter une photo")).getBoundsInRoot()
        val second = onNode(button("Ajouter une vidéo")).getBoundsInRoot()
        assertTrue(second.left >= first.right && second.top == first.top, "side by side: $first $second")
    }

    @Test
    fun a_text_action_is_bold_underlined_in_the_main_text_colour_with_a_48_dp_target() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                TextAction("Refuser", onClick = {})
            }
        }

        assertTrue(onNode(button("Refuser")).getBoundsInRoot().height >= 48.dp)
        val style = layoutOf("Refuser").layoutInput.style
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(TextDecoration.Underline, style.textDecoration)
        assertEquals(Color(0xFF22300D), style.color)
    }

    @Test
    fun a_destructive_text_action_keeps_the_error_colour_with_the_same_style() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                TextAction("Abandonner", onClick = {}, destructive = true)
            }
        }

        val style = layoutOf("Abandonner").layoutInput.style
        assertEquals(TextDecoration.Underline, style.textDecoration)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(Color(0xFFB3361E), style.color)
    }

    @Test
    fun the_pagination_buttons_are_outlined_48_dp_and_stack_on_a_narrow_width() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.width(220.dp)) {
                    PaginationBar(
                        pageLabel = "Page 2 sur 5",
                        previousLabel = "Précédent",
                        onPrevious = {},
                        previousEnabled = true,
                        nextLabel = "Suivant",
                        onNext = {},
                        nextEnabled = true,
                    )
                }
            }
        }

        val previous = onNode(button("Précédent")).getBoundsInRoot()
        val next = onNode(button("Suivant")).getBoundsInRoot()
        assertTrue(previous.height >= 48.dp && next.height >= 48.dp)
        assertTrue(next.top >= previous.bottom, "stacked: $previous $next")
        assertEquals(220.dp, previous.width)
        onNodeWithText("Page 2 sur 5").assertExists()
    }

    @Test
    fun the_pagination_stays_on_one_line_when_it_fits() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.width(412.dp)) {
                    PaginationBar("Page 2 sur 5", "Précédent", {}, true, "Suivant", {}, true)
                }
            }
        }

        val previous = onNode(button("Précédent")).getBoundsInRoot()
        val label = onNodeWithText("Page 2 sur 5").getBoundsInRoot()
        val next = onNode(button("Suivant")).getBoundsInRoot()
        assertTrue(previous.right <= label.left && label.right <= next.left, "one line: $previous $label $next")
        assertTrue(previous.top < label.bottom && next.top < label.bottom)
    }
}
