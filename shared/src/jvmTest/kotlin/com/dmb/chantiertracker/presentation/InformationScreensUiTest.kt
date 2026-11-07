package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.auth.welcome.ProductPresentationContent
import com.dmb.chantiertracker.presentation.billing.LocalFounderOffer
import com.dmb.chantiertracker.presentation.billing.LocalServerBillingStatus
import com.dmb.chantiertracker.presentation.help.DiscoverScreen
import com.dmb.chantiertracker.presentation.help.HelpScreen
import com.dmb.chantiertracker.presentation.help.HelpViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.offer.LocalOpenMyOffer
import com.dmb.chantiertracker.presentation.offer.LocalPlanUsage
import com.dmb.chantiertracker.presentation.offer.MyOfferScreen
import com.dmb.chantiertracker.presentation.offer.MyOfferViewModel
import com.dmb.chantiertracker.presentation.offer.PlanLimitKind
import com.dmb.chantiertracker.presentation.offer.PlanLimitNotice
import com.dmb.chantiertracker.presentation.offer.planLimitKind
import com.dmb.chantiertracker.presentation.offer.reachedCount
import com.dmb.chantiertracker.presentation.offer.wholeHistoryMonths
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeLaunchStatusRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeSupportContactRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InformationScreensUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val founder = PlanUsage(
        Plan.FREE, projectsLimit = 3, projectsUsed = 1, photosUsed = 12, photosLimit = 150, videosUsed = 2, videosLimit = 5,
        videoDurationLimitSeconds = 120, supervisorsUsed = 1, supervisorsLimit = 3, isFounder = true, refreshedAtEpochMillis = 1_793_000_000_000,
    )

    private fun ComposeUiTest.mount(status: BillingAvailability = BillingAvailability.UNKNOWN, height: Int = 700, content: @Composable () -> Unit) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(LocalServerBillingStatus provides status) {
                        Surface { Box(Modifier.size(412.dp, height.dp)) { content() } }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun the_history_is_worked_out_from_the_days() {
        assertEquals(6, wholeHistoryMonths(180))
        assertEquals(12, wholeHistoryMonths(360))
        assertNull(wholeHistoryMonths(30))
        assertNull(wholeHistoryMonths(45))
    }

    @Test
    fun my_offer_shows_the_history_from_the_plan_usage() = runComposeUiTest {
        val viewModel = MyOfferViewModel(
            FakeAccountRepository(founder.copy(historyDaysLimit = 180, historyDaysLimitKnown = true)),
            FakeLaunchStatusRepository(BillingAvailability.CLOSED),
            FakeConnectivityObserver(initiallyOnline = true),
        )
        mount { MyOfferScreen(viewModel = viewModel) }

        awaitText("Historique conservé 6 mois")
        onNodeWithText("limité", substring = true).assertDoesNotExist()
    }

    @Test
    fun the_discover_screen_shows_the_history_of_the_offer_and_no_other_six_months() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(LocalFounderOffer provides offer) {
                        Surface { Box(Modifier.size(412.dp, 700.dp)) { DiscoverScreen() } }
                    }
                }
            }
        }
        waitForIdle()

        onAllNodes(hasText("mois", substring = true)).assertCountEquals(1)
        onNodeWithText("Historique conservé 6 mois").assertExists()
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun offerViewModel(status: BillingAvailability, online: Boolean = true, usage: PlanUsage? = founder) =
        MyOfferViewModel(FakeAccountRepository(usage), FakeLaunchStatusRepository(status), FakeConnectivityObserver(initiallyOnline = online))

    private fun ComposeUiTest.welcome(status: BillingAvailability, onCreate: () -> Unit = {}, onSignIn: () -> Unit = {}) = mount(status) {
        WelcomeScreen(onCreateAccount = onCreate, onSignIn = onSignIn, onDiscoverPlans = null, onOpenLegalDocument = {})
    }

    private fun ComposeUiTest.presentation(status: BillingAvailability) = mount(status) {
        ProductPresentationContent(onClose = {})
    }

    @Test
    fun the_welcome_screen_offers_its_two_buttons() = runComposeUiTest {
        var created = 0
        var signedIn = 0
        welcome(BillingAvailability.CLOSED, { created++ }, { signedIn++ })
        onNodeWithText("Créer un compte").performClick()
        onNodeWithText("Se connecter").performClick()
        waitForIdle()

        assertEquals(1, created)
        assertEquals(1, signedIn)
    }

    @Test
    fun the_presentation_sheet_presents_the_product() = runComposeUiTest {
        presentation(BillingAvailability.CLOSED)
        onNodeWithText("Ton chantier sous contrôle, même à distance", ignoreCase = true).assertExists()
        onNodeWithText("Le stock se tient tout seul").assertExists()
    }

    @Test
    fun the_founder_offer_shows_while_billing_is_closed() = runComposeUiTest {
        presentation(BillingAvailability.CLOSED)
        onNodeWithText("Offre fondateur", ignoreCase = true).assertExists()
    }

    @Test
    fun the_founder_offer_goes_once_billing_is_open() = runComposeUiTest {
        presentation(BillingAvailability.OPEN)
        onNodeWithText("Offre fondateur", ignoreCase = true).assertDoesNotExist()
    }

    private val offer = FounderOffer(
        projects = 3, photos = 150, videos = 5, videoMaxSeconds = 120, supervisorsPerProject = 3, pdfExport = true,
        historyDays = 180,
    )

    private fun ComposeUiTest.welcomeWithOffer(status: BillingAvailability, founderOffer: FounderOffer?) = setContent {
        customAppLocale = "fr"
        AppEnvironment {
            AppTheme {
                CompositionLocalProvider(LocalServerBillingStatus provides status, LocalFounderOffer provides founderOffer) {
                    Surface {
                        Box(Modifier.size(412.dp, 700.dp)) {
                            ProductPresentationContent(onClose = {})
                        }
                    }
                }
            }
        }
    }

    @Test
    fun the_founder_offer_shows_the_figures_of_the_api() = runComposeUiTest {
        welcomeWithOffer(BillingAvailability.CLOSED, offer)

        listOf(
            "3 projets", "150 photos", "5 vidéos de 2:00 maximum", "3 superviseurs par projet", "Export PDF inclus",
            "Historique conservé 6 mois",
        ).forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun the_offer_shows_no_duration_and_six_months_only_for_the_history() = runComposeUiTest {
        welcomeWithOffer(BillingAvailability.CLOSED, offer)

        onNodeWithText("Durée", substring = true).assertDoesNotExist()
        onAllNodes(hasText("6 mois", substring = true)).assertCountEquals(1)
        onNodeWithText("Historique conservé 6 mois").assertExists()
    }

    @Test
    fun an_open_switch_hides_the_figures_too() = runComposeUiTest {
        welcomeWithOffer(BillingAvailability.OPEN, offer)

        onNodeWithText("3 projets").assertDoesNotExist()
    }

    @Test
    fun my_offer_takes_its_limits_from_the_plan_usage_never_from_the_public_offer() = runComposeUiTest {
        val viewModel = MyOfferViewModel(
            FakeAccountRepository(founder),
            FakeLaunchStatusRepository(BillingAvailability.CLOSED, offer.copy(projects = 99)),
            FakeConnectivityObserver(initiallyOnline = true),
        )
        mount { MyOfferScreen(viewModel = viewModel) }

        awaitText("Projets : 1 sur 3")
        onNodeWithText("99", substring = true).assertDoesNotExist()
    }

    @Test
    fun an_unknown_switch_promises_nothing() = runComposeUiTest {
        presentation(BillingAvailability.UNKNOWN)
        onNodeWithText("Offre fondateur", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun my_offer_with_billing_closed_shows_the_founder_offer_and_the_real_usage() = runComposeUiTest {
        val viewModel = offerViewModel(BillingAvailability.CLOSED)
        mount { MyOfferScreen(viewModel = viewModel) }

        awaitText("Offre fondateur : gratuite pendant le lancement.")
        onNodeWithText("Projets : 1 sur 3").assertExists()
        onNodeWithText("Photos : 12 sur 150").assertExists()
        onNodeWithText("Vidéos : 2 sur 5").assertExists()
        onNodeWithText("Superviseurs par projet : 1 sur 3").assertExists()
        onNodeWithText("Durée maximale par vidéo : 2:00").assertExists()
    }

    @Test
    fun my_offer_with_billing_open_keeps_the_usage_without_the_launch_offer() = runComposeUiTest {
        val viewModel = offerViewModel(BillingAvailability.OPEN)
        mount { MyOfferScreen(viewModel = viewModel) }

        awaitText("Projets : 1 sur 3")
        onNodeWithText("gratuite pendant le lancement", substring = true).assertDoesNotExist()
    }

    @Test
    fun my_offer_offline_shows_the_last_values_with_their_date() = runComposeUiTest {
        val viewModel = offerViewModel(BillingAvailability.UNKNOWN, online = false)
        mount { MyOfferScreen(viewModel = viewModel) }

        awaitText("Hors ligne : dernières valeurs connues, du ")
        onNodeWithText("Projets : 1 sur 3").assertExists()
    }

    @Test
    fun my_offer_offline_with_nothing_known_says_what_to_do() = runComposeUiTest {
        val viewModel = offerViewModel(BillingAvailability.UNKNOWN, online = false, usage = null)
        mount { MyOfferScreen(viewModel = viewModel) }

        awaitText("Connecte-toi à Internet une première fois")
    }

    @Test
    fun a_refused_action_says_which_limit_shows_the_usage_and_leads_to_the_offer() = runComposeUiTest {
        var opened = 0
        mount {
            CompositionLocalProvider(
                LocalPlanUsage provides founder.copy(projectsUsed = 3),
                LocalOpenMyOffer provides { opened++ },
            ) { PlanLimitNotice(PlanLimitKind.PROJECTS) }
        }

        onNodeWithText("Tu as atteint la limite de projets de ta formule. Usage : 3 sur 3.").assertExists()
        onNodeWithText("Voir mon offre").performClick()
        waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun a_counter_below_its_limit_is_not_the_one_that_refused() {
        assertNull(founder.reachedCount(PlanLimitKind.PROJECTS))
        assertEquals(5 to 5, founder.copy(videosUsed = 5).reachedCount(PlanLimitKind.MEDIA))
        assertEquals(PlanLimitKind.MEDIA, SyncIssueTarget.ATTACHMENT.planLimitKind())
        assertNull(SyncIssueTarget.STAGE.planLimitKind())
    }

    @Test
    fun an_empty_project_list_explains_and_offers_the_action() = runComposeUiTest {
        var created = 0
        val viewModel = ProjectsViewModel(FakeProjectRepository(projects = emptyList()), FakeInvitationRepository(), ProjectSortHolder())
        mount { ProjectsScreen(onProjectClick = {}, onCreateProject = { created++ }, viewModel = viewModel) }

        awaitText("Aucun projet pour l'instant")
        onNodeWithText("Créer mon premier projet").performClick()
        waitForIdle()
        assertEquals(1, created)
    }

    private val supportAddress = "aide@exemple.test"

    private fun helpViewModel(
        mailed: MutableList<Triple<String, String, String>>,
        failing: Boolean = false,
        email: String? = supportAddress,
    ) = HelpViewModel(
        { address, subject, body ->
            if (failing) error("no mail app")
            mailed += Triple(address, subject, body)
        },
        AppConfig(FakeBuildInfo(isDebug = false, appVersion = "1.2.3")),
        FakeSupportContactRepository(email),
    )

    @Test
    fun reporting_a_problem_prepares_the_mail_without_anything_personal() = runComposeUiTest {
        val mailed = mutableListOf<Triple<String, String, String>>()
        val viewModel = helpViewModel(mailed)
        mount { HelpScreen(onDiscover = {}, viewModel = viewModel) }

        onNodeWithText("Signaler un problème ou faire une suggestion").performScrollTo().performClick()
        waitForIdle()

        val (address, subject, body) = mailed.single()
        assertEquals(supportAddress, address)
        assertEquals("ChantierTracker : problème ou suggestion", subject)
        assertTrue("Version de l'app : 1.2.3" in body, body)
        assertFalse("@" in body, body)
        assertFalse("token" in body.lowercase(), body)
    }

    @Test
    fun the_help_screen_shows_the_support_address() = runComposeUiTest {
        val viewModel = helpViewModel(mutableListOf())
        mount { HelpScreen(onDiscover = {}, viewModel = viewModel) }

        onNodeWithText(supportAddress).assertExists()
    }

    @Test
    fun without_an_address_the_contact_is_hidden() = runComposeUiTest {
        val viewModel = helpViewModel(mutableListOf(), email = null)
        mount { HelpScreen(onDiscover = {}, viewModel = viewModel) }

        onNodeWithText("Signaler un problème ou faire une suggestion").assertDoesNotExist()
        onNodeWithText("Une question, un souci ?", ignoreCase = true).assertDoesNotExist()
        onNodeWithText("Puis-je travailler sans réseau ?").assertExists()
    }

    @Test
    fun without_a_mail_app_the_address_is_shown() = runComposeUiTest {
        val viewModel = helpViewModel(mutableListOf(), failing = true)
        mount { HelpScreen(onDiscover = {}, viewModel = viewModel) }

        onNodeWithText("Signaler un problème ou faire une suggestion").performScrollTo().performClick()

        awaitText("Écris-nous à $supportAddress")
    }

    @Test
    fun the_faq_covers_every_topic_asked() = runComposeUiTest {
        val viewModel = helpViewModel(mutableListOf())
        mount { HelpScreen(onDiscover = {}, viewModel = viewModel) }

        listOf(
            "Puis-je travailler sans réseau ?", "Que veut dire « problème de synchro » ?", "Que peut faire le propriétaire ?",
            "Et un administrateur ?", "Et un superviseur ?", "Comment inviter quelqu'un ?", "Comment exporter un projet en PDF ?",
            "Les photos sont-elles compressées ?", "Quelle taille pour une vidéo ?", "Comment supprimer mon compte ?",
        ).forEach { onNodeWithText(it).assertExists() }
    }
}
