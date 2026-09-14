package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectRole
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ADR-54 point 3/5: "Refuser" used to fire declineInvitation() straight away —
// this pins the new confirmation step in front of it.
@OptIn(ExperimentalTestApi::class)
class ProjectsScreenDeclineInvitationUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val villa = Project("p1", "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")

    @Test
    fun declining_an_incoming_invitation_requires_confirmation() = runComposeUiTest {
        val invitationRepo = FakeInvitationRepository().apply {
            incoming = listOf(
                IncomingInvitation(
                    token = "tok-1", projectId = 9L, projectName = "Villa Vidal",
                    role = ProjectRole.SUPERVISOR, invitedByName = null,
                    createdAt = null, expiresAt = null,
                ),
            )
        }
        val vm = ProjectsViewModel(FakeProjectRepository(projects = listOf(villa)), invitationRepo, ProjectSortHolder())
            .also { it.onEnter() }

        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { ProjectsScreen(onProjectClick = {}, viewModel = vm) } }
        }
        waitForIdle()

        onNodeWithText("Refuser").performClick()
        waitForIdle()

        assertTrue(invitationRepo.declined.isEmpty(), "le refus ne doit pas s'exécuter avant confirmation")
        onNodeWithText("Refuser cette invitation ?").assertIsDisplayed()

        // Two "Refuser" nodes now coexist: the dimmed card button behind the
        // scrim, and the dialog's own confirm button — the dialog's is the
        // second/topmost one in the merged semantics tree.
        onAllNodesWithText("Refuser")[1].performClick()
        waitForIdle()

        assertEquals(listOf("tok-1"), invitationRepo.declined)
    }
}
