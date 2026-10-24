package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.MemberLosingAccess
import com.dmb.chantiertracker.domain.model.OwnedProjectToDelete
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectToLeave
import com.dmb.chantiertracker.presentation.account.DeleteAccountScreen
import com.dmb.chantiertracker.presentation.account.DeleteAccountViewModel
import com.dmb.chantiertracker.presentation.account.FakeAccountDeletionRepository
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AccountMenuBody
import com.dmb.chantiertracker.presentation.navigation.LoginNotice
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DeleteAccountScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest
    fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val fullPreview = AccountDeletionPreview(
        canDelete = true, blockers = emptyList(), founder = true,
        ownedProjects = listOf(
            OwnedProjectToDelete("Villa Dupont", listOf(MemberLosingAccess("Yann Leroy", ProjectRole.SUPERVISOR), MemberLosingAccess("Léa Martin", ProjectRole.ADMIN))),
            OwnedProjectToDelete("Hangar", emptyList()),
        ),
        memberships = listOf(ProjectToLeave("Toiture Marchand", ProjectRole.SUPERVISOR)),
        pendingInvitations = 2,
    )

    private fun ComposeUiTest.mount(content: @Composable () -> Unit) = setContent {
        customAppLocale = "fr"
        AppEnvironment { AppTheme { Surface { content() } } }
    }

    private fun ComposeUiTest.screen(
        repo: FakeAccountDeletionRepository,
        online: Boolean = true,
        onManageUsers: () -> Unit = {},
        onManageSubscription: () -> Unit = {},
    ) = mount {
        DeleteAccountScreen(
            onCancel = {},
            onManageUsers = onManageUsers,
            onManageSubscription = onManageSubscription,
            viewModel = DeleteAccountViewModel(repo, FakeConnectivityObserver(initiallyOnline = online)),
        )
    }

    @Test
    fun the_preview_names_the_projects_deleted_and_who_loses_access() = runComposeUiTest {
        screen(FakeAccountDeletionRepository(fullPreview))

        onNodeWithText("Projets supprimés").assertExists()
        onNodeWithText("• Villa Dupont").assertExists()
        onNodeWithText("Yann Leroy (superviseur) et Léa Martin (administrateur) perdront l'accès.").assertExists()
        onNodeWithText("• Hangar").assertExists()
        onNodeWithText("Projets que tu quittes").assertExists()
        onNodeWithText("• Toiture Marchand (superviseur)").assertExists()
    }

    @Test
    fun the_founder_status_lost_and_the_invitations_are_said() = runComposeUiTest {
        screen(FakeAccountDeletionRepository(fullPreview))

        onNodeWithText("• Tu perds ton statut Fondateur : il n'est pas rendu, même si tu crées un nouveau compte.").assertExists()
        onNodeWithText("• 2 invitations en attente seront supprimées.").assertExists()
    }

    @Test
    fun a_single_member_losing_access_reads_in_the_singular() = runComposeUiTest {
        screen(FakeAccountDeletionRepository(fullPreview.copy(ownedProjects = listOf(OwnedProjectToDelete("Villa Dupont", listOf(MemberLosingAccess("Yann Leroy", ProjectRole.SUPERVISOR)))))))

        onNodeWithText("Yann Leroy (superviseur) perdra l'accès.").assertExists()
    }

    @Test
    fun blockers_give_their_reason_and_the_way_out_and_hide_the_confirmation() = runComposeUiTest {
        val opened = mutableListOf<String>()
        screen(
            FakeAccountDeletionRepository(fullPreview.copy(canDelete = false, blockers = listOf(DeletionBlocker.LAST_SUPER_ADMIN, DeletionBlocker.SUBSCRIPTION_STILL_RENEWING))),
            onManageUsers = { opened += "users" },
            onManageSubscription = { opened += "billing" },
        )

        onNodeWithText("Suppression impossible pour l'instant").assertExists()
        onNodeWithText("Gérer les utilisateurs").performClick()
        onNodeWithText("Gérer l'abonnement").performClick()
        assertEquals(listOf("users", "billing"), opened)
        onNodeWithText("Supprimer définitivement mon compte").assertDoesNotExist()
    }

    @Test
    fun offline_a_clear_message_and_nothing_to_send() = runComposeUiTest {
        screen(FakeAccountDeletionRepository(fullPreview), online = false)

        onNodeWithText("Tu es hors ligne. La suppression du compte se fait en ligne : reconnecte-toi à Internet pour continuer.").assertExists()
        onNodeWithText("Supprimer définitivement mon compte").assertDoesNotExist()
    }

    @Test
    fun the_button_waits_for_the_password_and_the_checkbox() = runComposeUiTest {
        val repo = FakeAccountDeletionRepository(fullPreview)
        screen(repo)

        val submit = onNodeWithText("Supprimer définitivement mon compte")
        submit.performScrollTo().assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput("MonMotDePasse1!")
        submit.assertIsNotEnabled()
        onNodeWithText("Je comprends que la suppression est définitive : mon compte et mes projets ne pourront pas être récupérés.").performScrollTo().performClick()
        submit.assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(listOf("MonMotDePasse1!"), repo.deletions)
    }

    @Test
    fun a_wrong_password_is_said_under_the_field() = runComposeUiTest {
        val repo = FakeAccountDeletionRepository(fullPreview).apply { failure = DomainException.InvalidCurrentPassword }
        screen(repo)

        onNode(hasSetTextAction()).performScrollTo().performTextInput("faux")
        onNodeWithText("Je comprends que la suppression est définitive : mon compte et mes projets ne pourront pas être récupérés.").performScrollTo().performClick()
        onNodeWithText("Supprimer définitivement mon compte").performClick()
        waitForIdle()

        onNodeWithText("Mot de passe incorrect.").assertExists()
    }

    @Test
    fun the_account_menu_leads_to_the_deletion() = runComposeUiTest {
        var opened = false
        mount {
            Column {
                AccountMenuBody(userName = "Jean Marchand", email = "jean@chantier.dev", plan = Plan.FREE, onSubscription = {}, onLogout = {}, onDeleteAccount = { opened = true })
            }
        }

        onNodeWithText("Supprimer mon compte").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun the_sign_in_screen_says_the_account_was_deleted() = runComposeUiTest {
        mount {
            LoginScreen(
                onNavigateToRegister = {},
                onNavigateToForgotPassword = {},
                onOpenLegalDocument = {},
                notice = LoginNotice.AccountDeleted,
                viewModel = LoginViewModel(FakeAuthRepository(), FakeCheckoutLauncher(), FakeBillingRepository()),
            )
        }

        onNodeWithText("Ton compte a été supprimé.").assertExists()
    }
}
