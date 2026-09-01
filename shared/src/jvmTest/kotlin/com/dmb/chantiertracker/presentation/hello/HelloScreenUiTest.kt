package com.dmb.chantiertracker.presentation.hello

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.WelcomeMessage
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HelloScreenUiTest {

    @Test
    fun renders_welcome_message() = runComposeUiTest {
        setContent {
            AppTheme {
                HelloContent(
                    HelloUiState.Content(
                        WelcomeMessage(
                            title = "Hello ChantierTracker",
                            platformName = "JVM Test",
                            apiBaseUrl = "https://api.chantiertracker.com/api/v1",
                        ),
                    ),
                )
            }
        }

        onNodeWithText("Hello ChantierTracker").assertIsDisplayed()
        onNodeWithText("Plateforme : JVM Test").assertIsDisplayed()
    }
}
