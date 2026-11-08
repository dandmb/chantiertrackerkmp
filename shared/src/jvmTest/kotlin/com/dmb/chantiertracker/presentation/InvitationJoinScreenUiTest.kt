package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.dmb.chantiertracker.domain.model.InvitationPreview
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.invitations.InvitationAcceptScreen
import com.dmb.chantiertracker.presentation.invitations.InvitationAcceptViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InvitationJoinScreenUiTest {

    private val invitations = FakeInvitationRepository().apply {
        preview = InvitationPreview(7L, "Villa Vidal", InvitationStatus.PENDING, ProjectRole.SUPERVISOR, "Paul Martin")
    }
    private val auth = com.dmb.chantiertracker.support.FakeAuthRepository().apply {
        emitState(com.dmb.chantiertracker.domain.model.AuthState.Authenticated(
            com.dmb.chantiertracker.domain.model.User(1, "moi@chantier.dev", "Moi", true, com.dmb.chantiertracker.domain.model.GlobalRole.USER),
        ))
    }
    private var signOuts = 0

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun ComposeUiTest.mount() = setContent {
        customAppLocale = "fr"
        AppEnvironment {
            AppTheme {
                InvitationAcceptScreen(
                    token = "tok",
                    onAccepted = {},
                    onSignOut = { signOuts++ },
                    viewModel = InvitationAcceptViewModel(
                        invitations, FakeProjectRepository(),
                        com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher(), auth,
                    ),
                )
            }
        }
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun runPhoneTest(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = 412, height = 892) { block() }

    @Test
    fun the_link_shows_the_project_the_inviter_and_the_role_before_anything_is_accepted() = runPhoneTest {
        mount()

        awaitText("Villa Vidal")
        onNodeWithText("Paul Martin", substring = true).assertExists()
        onNodeWithText("Superviseur", substring = true).assertExists()
        onNode(hasText("Rejoindre le projet") and hasClickAction()).assertExists()
        onNode(hasText("Refuser") and hasClickAction()).assertExists()
        assertEquals(emptyList(), invitations.accepted)
    }

    @Test
    fun only_the_join_button_accepts() = runPhoneTest {
        mount()
        awaitText("Villa Vidal")

        onNode(hasText("Rejoindre le projet") and hasClickAction()).performClick()

        awaitText("Bienvenue dans Villa Vidal")
        assertEquals(listOf("tok"), invitations.accepted)
    }

    @Test
    fun refusing_asks_first_then_declines_without_joining() = runPhoneTest {
        mount()
        awaitText("Villa Vidal")

        onNode(hasText("Refuser") and hasClickAction()).performClick()
        awaitText("Refuser cette invitation ?")
        assertEquals(emptyList(), invitations.declinedTokens)
        onAllNodes(hasText("Refuser") and hasClickAction()).fetchSemanticsNodes().size.let { assertEquals(2, it) }
        onAllNodes(hasText("Refuser") and hasClickAction())[1].performClick()

        awaitText("Invitation refusée")
        assertEquals(listOf("tok"), invitations.declinedTokens)
        assertEquals(emptyList(), invitations.accepted)
    }

    @Test
    fun without_an_inviter_the_inviter_line_is_left_out() = runPhoneTest {
        invitations.preview = InvitationPreview(7L, "Villa Vidal", InvitationStatus.PENDING, ProjectRole.ADMIN, null)
        mount()

        awaitText("Villa Vidal")
        onNodeWithText("Rôle : Administrateur").assertExists()
        assertEquals(0, onAllNodes(hasText("Invitation de", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun another_account_is_told_to_sign_in_with_the_right_one_and_can_sign_out() = runPhoneTest {
        invitations.acceptError = com.dmb.chantiertracker.domain.model.DomainException.InvitationForAnotherAccount
        mount()
        awaitText("Villa Vidal")

        onNode(hasText("Rejoindre le projet") and hasClickAction()).performClick()

        awaitText("Cette invitation est destinée à une autre adresse. Connecte-toi avec le bon compte.")
        assertEquals(0, onAllNodes(hasText("@", substring = true)).fetchSemanticsNodes().size, "no address shown")
        onNode(hasText("Se déconnecter") and hasClickAction()).performClick()
        assertEquals(1, signOuts)
    }
}
