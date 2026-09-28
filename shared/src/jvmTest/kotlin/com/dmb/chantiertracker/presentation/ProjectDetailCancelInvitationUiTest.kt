package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ADR-54 point 3/5: "Annuler" on a pending invitation used to fire
// cancelInvitation() straight away — this pins the new confirmation step.
@OptIn(ExperimentalTestApi::class)
class ProjectDetailCancelInvitationUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun cancelling_a_pending_invitation_requires_confirmation() = runComposeUiTest {
        val user = User(1, "jean@chantier.dev", "Jean Marchand", true, GlobalRole.USER)
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(user)) }
        val projectRepo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = 1L,
            ),
        )
        val invitationRepo = FakeInvitationRepository(
            listOf(Invitation(7L, "1", "lea@chantier.dev", ProjectRole.SUPERVISOR, 1L, "2026-09-01T10:00:00", null, InvitationStatus.PENDING)),
        )
        val vm = ProjectDetailViewModel(projectRepo, FakeStageRepository(), invitationRepo, auth).also { it.load("1") }

        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { ProjectDetailScreen(projectLocalId = "1", viewModel = vm) } }
        }
        waitForIdle()

        onNodeWithText("Annuler").performClick()
        waitForIdle()

        assertTrue(invitationRepo.cancelled.isEmpty(), "l'annulation ne doit pas s'exécuter avant confirmation")
        onNodeWithText("Annuler cette invitation ?").assertIsDisplayed()

        onNodeWithText("Annuler l'invitation").performClick()
        waitForIdle()

        assertEquals(listOf("1" to 7L), invitationRepo.cancelled)
    }
}
