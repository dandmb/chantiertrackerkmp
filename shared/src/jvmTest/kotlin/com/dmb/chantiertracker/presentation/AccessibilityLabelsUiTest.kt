package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AccessibilityLabelsUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val unnamedClickable = hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)

    @Test
    fun the_floating_create_button_carries_its_name_on_the_clickable_node() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { CreateFab("Nouveau projet", onClick = {}) } }
        }
        waitForIdle()

        onNode(hasClickAction() and hasContentDescription("Nouveau projet"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun a_date_field_is_announced_with_its_label_and_its_value() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Column {
                        DateField(label = "Date de début", value = "", onValueChange = {}, minDate = LocalDate(2026, 1, 1))
                        DateField(label = "Date de fin", value = "2026-11-20", onValueChange = {}, minDate = LocalDate(2026, 1, 1))
                    }
                }
            }
        }
        waitForIdle()

        onNode(hasClickAction() and hasContentDescription("Date de début", substring = true), useUnmergedTree = true).assertExists()
        onNode(hasClickAction() and hasContentDescription("Date de fin, 20-11-2026", substring = true), useUnmergedTree = true).assertExists()
        assertEquals(0, onAllNodes(unnamedClickable, useUnmergedTree = true).fetchSemanticsNodes().size, "no clickable without a name")
    }
}
