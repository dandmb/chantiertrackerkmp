package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DateFieldUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun tapping_the_field_opens_the_calendar_dialog() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    DateField(
                        label = "Date de début",
                        value = "",
                        onValueChange = {},
                        minDate = LocalDate(2026, 9, 4),
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("dateFieldTap:Date de début").performClick()
        waitForIdle()

        onNodeWithText("Annuler").assertIsDisplayed()
        onNodeWithText("OK").assertIsDisplayed()
    }
}
