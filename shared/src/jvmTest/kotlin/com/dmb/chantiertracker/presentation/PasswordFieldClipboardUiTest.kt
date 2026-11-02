package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PasswordFieldClipboardUiTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private class RecordingClipboard : Clipboard {
        var writes = 0
        override suspend fun getClipEntry(): ClipEntry? = null
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { writes++ }
        override val nativeClipboard: NativeClipboard get() = error("not used")
    }

    @Test
    fun a_revealed_password_never_reaches_the_clipboard() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(LocalClipboard provides clipboard) {
                        var password by remember { mutableStateOf("Password1234!") }
                        PasswordField(value = password, onValueChange = { password = it })
                    }
                }
            }
        }
        onNodeWithContentDescription("Afficher le mot de passe").performClick()
        waitForIdle()

        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextInputSelection(TextRange(0, 8))
        waitForIdle()
        val field = onNode(hasSetTextAction())
        field.assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.CopyText))
        listOf(Key.CtrlLeft, Key.MetaLeft).forEach { modifier ->
            field.performKeyInput {
                keyDown(modifier)
                pressKey(Key.C)
                keyUp(modifier)
            }
            waitForIdle()
        }

        assertEquals(0, clipboard.writes)
    }
}
