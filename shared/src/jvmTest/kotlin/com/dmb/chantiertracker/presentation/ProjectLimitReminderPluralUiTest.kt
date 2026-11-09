package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.offer.LocalPlanUsage
import com.dmb.chantiertracker.presentation.offer.ProjectLimitReminder
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProjectLimitReminderPluralUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun the_project_count_agrees_with_its_number_in_french() = runComposeUiTest {
        var used by mutableStateOf(0)
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(LocalPlanUsage provides PlanUsage(Plan.FREE, projectsLimit = 3, projectsUsed = used)) {
                        ProjectLimitReminder()
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("0 projet sur 3").assertIsDisplayed()

        used = 1
        waitForIdle()
        onNodeWithText("1 projet sur 3").assertIsDisplayed()

        used = 2
        waitForIdle()
        onNodeWithText("2 projets sur 3").assertIsDisplayed()
    }
}
