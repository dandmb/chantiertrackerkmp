package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.presentation.account.DeleteAccountScreen
import com.dmb.chantiertracker.presentation.account.DeleteAccountViewModel
import com.dmb.chantiertracker.presentation.account.FakeAccountDeletionRepository
import com.dmb.chantiertracker.presentation.billing.LocalBillingAvailability
import com.dmb.chantiertracker.presentation.billing.LocalInAppPaymentsAllowed
import com.dmb.chantiertracker.presentation.billing.historyRetentionNotice
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.AccountMenuBody
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.sync.instructionRes
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.stringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// Google Play payments policy (R6b): with in-app payments off, as on Android, nothing leads to a
// payment, even when the server has opened the paid plans. The plan stays readable.
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class NoInAppPaymentUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.mount(paymentsAllowed: Boolean, content: @Composable () -> Unit) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(
                        LocalBillingAvailability provides BillingAvailability.OPEN,
                        LocalInAppPaymentsAllowed provides paymentsAllowed,
                    ) {
                        Surface { Box(Modifier.size(412.dp, 892.dp)) { content() } }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.accountMenu(paymentsAllowed: Boolean, onSubscription: (() -> Unit)?) = mount(paymentsAllowed) {
        Column {
            AccountMenuBody(
                userName = "Jean Marchand", email = "jean@chantier.dev", plan = Plan.FREE,
                onSubscription = onSubscription, onLogout = {}, isFounder = true,
            )
        }
    }

    @Test
    fun the_account_menu_shows_the_plan_without_leading_to_the_subscription() = runComposeUiTest {
        accountMenu(paymentsAllowed = false, onSubscription = null)

        onNodeWithText("Mon abonnement").assertDoesNotExist()
        onNodeWithText("Formule Gratuite · Fondateur").assertExists()
        onNodeWithText("Supprimer mon compte").assertExists()
    }

    @Test
    fun the_welcome_screen_offers_no_plan_to_buy() = runComposeUiTest {
        val auth = FakeAuthRepository().apply {
            onboardingSeen = true
            emitState(AuthState.Unauthenticated)
        }
        mount(paymentsAllowed = false) { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Créer un compte")
        onNodeWithText("Découvrir nos formules").assertDoesNotExist()
        onNodeWithText("Passe directement à une formule payante, dès l'inscription.").assertDoesNotExist()
    }

    @Test
    fun the_welcome_screen_still_offers_the_plans_where_payments_are_allowed() = runComposeUiTest {
        val auth = FakeAuthRepository().apply {
            onboardingSeen = true
            emitState(AuthState.Unauthenticated)
        }
        mount(paymentsAllowed = true) { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Découvrir nos formules")
    }

    @Test
    fun limits_and_history_never_suggest_a_higher_tier() = runComposeUiTest {
        mount(paymentsAllowed = false) {
            Column {
                Text(DomainException.PlanLimitReached.localizedText())
                Text(historyRetentionNotice(30).orEmpty())
                Text(historyRetentionNotice(180).orEmpty())
                Text(stringResource(RefusalReason.PLAN_LIMIT.instructionRes(paymentsOffered = false)!!))
            }
        }

        onNodeWithText("Tu as atteint la limite de projets de ta formule.").assertExists()
        onNodeWithText("Historique limité aux 30 derniers jours.").assertExists()
        onNodeWithText("Historique limité aux 6 derniers mois.").assertExists()
        onNodeWithText("Supprime un projet, puis réessaie.").assertExists()
        onNodeWithText("palier supérieur", substring = true).assertDoesNotExist()
        onNodeWithText("Change de formule", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_renewing_subscription_blocks_the_deletion_without_a_way_into_the_subscription() = runComposeUiTest {
        val preview = AccountDeletionPreview(false, listOf(DeletionBlocker.SUBSCRIPTION_STILL_RENEWING), founder = false, ownedProjects = emptyList(), memberships = emptyList(), pendingInvitations = 0)
        mount(paymentsAllowed = false) {
            DeleteAccountScreen(
                onCancel = {}, onManageUsers = {}, onManageSubscription = {},
                viewModel = DeleteAccountViewModel(FakeAccountDeletionRepository(preview), FakeConnectivityObserver(initiallyOnline = true)),
            )
        }

        awaitText("Suppression impossible pour l'instant")
        onNodeWithText("Gérer l'abonnement").assertDoesNotExist()
        onNodeWithText("Tu as un abonnement en cours : il doit être annulé avant de supprimer ton compte.").assertExists()
        onNodeWithText("site", substring = true).assertDoesNotExist()
    }
}
