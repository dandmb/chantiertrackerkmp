package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectsScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val villa = Project("p1", "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")

    @Test
    fun pull_to_refresh_gesture_on_the_list_triggers_a_sync() = runComposeUiTest {
        val repo = FakeProjectRepository(projects = listOf(villa))
        val vm = ProjectsViewModel(repo, FakeInvitationRepository(), ProjectSortHolder())

        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { ProjectsScreen(onProjectClick = {}, viewModel = vm) } }
        }
        waitForIdle()
        val baseline = repo.refreshCount

        onNodeWithText("Villa Vidal").performTouchInput { swipeDown(startY = centerY, endY = centerY + 900f) }
        waitForIdle()

        assertTrue(repo.refreshCount > baseline, "le geste tirer-pour-rafraîchir déclenche une synchro (repo.refresh)")
    }
}
