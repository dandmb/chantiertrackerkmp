package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FieldLabelBelowTopBarUiTest {

    private val fields = List(8) { FocusRequester() }
    private val tallField = 6
    private lateinit var scroll: ScrollState
    private var keyboard by androidx.compose.runtime.mutableStateOf(0.dp)

    private fun ComposeUiTest.landscapeForm() = setContent {
        AppEnvironment {
            AppTheme {
                Column(Modifier.fillMaxSize().padding(bottom = keyboard)) {
                    Box(Modifier.fillMaxWidth().height(64.dp).background(Color.DarkGray).testTag("barre"))
                    scroll = rememberScrollState()
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(24.dp).testTag("formulaire"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        fields.forEachIndexed { index, requester ->
                            AppOutlinedTextField(
                                value = "Valeur ${index + 1}",
                                onValueChange = {},
                                label = { Text("Champ ${index + 1}") },
                                minLines = if (index == tallField) 8 else 1,
                                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.labelTop(index: Int) =
        onNodeWithText("Champ $index", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    private fun ComposeUiTest.barBottom() =
        onNodeWithTag("barre").fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    private fun ComposeUiTest.focus(index: Int) {
        runOnIdle { fields[index - 1].requestFocus() }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun with_the_keyboard_open_going_back_up_field_by_field_keeps_each_label_below_the_top_bar() = runDesktopComposeUiTest(width = 800, height = 400) {
        landscapeForm()
        waitForIdle()
        focus(3)
        keyboard = 230.dp
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        for (index in listOf(4, 5, 6)) focus(index)

        val hidden = mutableListOf<String>()
        for (index in listOf(5, 4, 3, 2, 1)) {
            focus(index)
            if (labelTop(index) < barBottom()) hidden += "champ $index : étiquette à ${labelTop(index)}, barre jusqu'à ${barBottom()}"
        }
        assertTrue(hidden.isEmpty(), hidden.joinToString())
    }

    @Test
    fun a_field_taller_than_the_space_left_by_the_keyboard_shows_its_label_when_reached_from_below() = runDesktopComposeUiTest(width = 800, height = 400) {
        landscapeForm()
        waitForIdle()
        keyboard = 230.dp
        waitForIdle()
        focus(8)

        focus(tallField + 1)

        assertTrue(labelTop(tallField + 1) >= barBottom(), "étiquette du champ haut à ${labelTop(tallField + 1)}, barre jusqu'à ${barBottom()}")
    }
}
