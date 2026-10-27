package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectDetailRemoveMemberUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val invitations = FakeInvitationRepository()

    private fun ComposeUiTest.showProjectAs(userId: Long, locale: String = "fr") {
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(userId, "u@x.dev", "U", true, GlobalRole.USER))) }
        val projects = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = 1L,
            ),
            members = listOf(
                ProjectMember(1, "Jean Marchand", "jean@chantier.dev", ProjectRole.ADMIN),
                ProjectMember(9, "Awa Diallo", "awa@chantier.dev", ProjectRole.SUPERVISOR),
            ),
        )
        val vm = ProjectDetailViewModel(projects, FakeStageRepository(), invitations, auth).also { it.load("1") }
        setContent {
            customAppLocale = locale
            AppEnvironment { AppTheme { ProjectDetailScreen(projectLocalId = "1", viewModel = vm) } }
        }
        waitForIdle()
    }

    @Test
    fun the_owner_removes_a_member_after_confirming() = runComposeUiTest {
        showProjectAs(userId = 1)

        onNodeWithContentDescription("Retirer Awa Diallo du projet").performScrollTo().performClick()
        waitForIdle()
        assertTrue(invitations.removedMembers.isEmpty(), "nothing before the confirmation")
        onNodeWithText("Retirer Awa Diallo ?").assertIsDisplayed()

        onNodeWithText("Retirer du projet").performClick()
        waitForIdle()

        assertEquals(listOf("1" to 9L), invitations.removedMembers)
    }

    @Test
    fun cancelling_the_confirmation_removes_nobody() = runComposeUiTest {
        showProjectAs(userId = 1)

        onNodeWithContentDescription("Retirer Awa Diallo du projet").performScrollTo().performClick()
        onNodeWithText("Annuler").performClick()
        waitForIdle()

        assertTrue(invitations.removedMembers.isEmpty())
        onNodeWithText("Retirer Awa Diallo ?").assertDoesNotExist()
    }

    @Test
    fun the_owner_is_never_offered_to_remove_themselves() = runComposeUiTest {
        showProjectAs(userId = 1)

        assertEquals(0, onAllNodes(hasContentDescription("Retirer Jean Marchand du projet")).fetchSemanticsNodes().size)
    }

    @Test
    fun a_member_who_is_not_the_owner_sees_no_removal() = runComposeUiTest {
        showProjectAs(userId = 9)

        assertEquals(0, onAllNodes(hasContentDescription("Retirer", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun a_refusal_is_explained_in_the_members_section() = runComposeUiTest {
        invitations.removeMemberError = DomainException.LastAdminRemoval
        showProjectAs(userId = 1)

        onNodeWithContentDescription("Retirer Awa Diallo du projet").performScrollTo().performClick()
        onNodeWithText("Retirer du projet").performClick()
        waitForIdle()

        onNodeWithText("Un projet garde au moins un administrateur : ce membre ne peut pas être retiré.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun in_english_the_confirmation_reads_remove_from_the_project() = runComposeUiTest {
        showProjectAs(userId = 1, locale = "en")

        onNodeWithContentDescription("Remove Awa Diallo from the project").performScrollTo().performClick()
        onNodeWithText("Remove Awa Diallo?").assertIsDisplayed()
        onNodeWithText("Remove from the project").performClick()
        waitForIdle()

        assertEquals(listOf("1" to 9L), invitations.removedMembers)
    }
}
