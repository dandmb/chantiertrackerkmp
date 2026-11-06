package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
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

@OptIn(ExperimentalTestApi::class)
class EmptyStateActionsUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun ComposeUiTest.showEmptyProjectAs(userId: Long, members: List<ProjectMember> = emptyList()) {
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(userId, "u@x.dev", "U", true, GlobalRole.USER))) }
        val projects = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = null,
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = 1L,
            ),
            members = members,
        )
        val vm = ProjectDetailViewModel(projects, FakeStageRepository(), FakeInvitationRepository(), auth).also { it.load("1") }
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { ProjectDetailScreen(projectLocalId = "1", viewModel = vm) } }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.buttonsReading(text: String): Int =
        onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size

    @Test
    fun the_empty_stages_section_shows_only_its_sentence_under_the_band_action() = runComposeUiTest {
        showEmptyProjectAs(userId = 1)

        onNodeWithText("Aucune étape pour l'instant", substring = true).performScrollTo()
        assertEquals(1, buttonsReading("Nouvelle étape"), "the band keeps its action")
        assertEquals(0, buttonsReading("Ajouter une étape"), "no second button in the empty state")
    }

    @Test
    fun the_empty_members_and_invitations_sections_show_only_their_sentence() = runComposeUiTest {
        showEmptyProjectAs(userId = 1)

        onNodeWithText("Aucun membre pour l'instant", substring = true).performScrollTo()
        onNodeWithText("Aucune invitation en attente", substring = true).performScrollTo()
        assertEquals(1, buttonsReading("Inviter un superviseur"), "one invite action, in the members band")
    }

    @Test
    fun a_supervisor_sees_the_empty_sentence_without_any_invite_action() = runComposeUiTest {
        showEmptyProjectAs(
            userId = 9,
            members = listOf(ProjectMember(9, "Awa Diallo", "awa@chantier.dev", ProjectRole.SUPERVISOR)),
        )

        assertEquals(0, buttonsReading("Inviter un superviseur"))
    }
}
