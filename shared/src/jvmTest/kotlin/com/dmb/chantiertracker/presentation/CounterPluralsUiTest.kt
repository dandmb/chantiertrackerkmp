package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.admin.AdminUserRow
import com.dmb.chantiertracker.presentation.billing.historyRetentionNotice
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.offer.FounderOfferFigures
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CounterPluralsUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun user(projects: Long) = AdminUser(
        id = 2, email = "amelie@chantier.dev", name = "Amélie Roy", active = true,
        globalRole = GlobalRole.USER, projectCount = projects, createdAt = "2026-08-15T09:00:00",
        plan = Plan.FREE, planSource = null, planExpiresAt = null,
    )

    private val singleLimits = PlanUsage(
        plan = Plan.FREE, projectsLimit = 1, projectsUsed = 0, photosUsed = 1, photosLimit = 1,
        videosUsed = 1, videosLimit = 1, supervisorsUsed = 1, supervisorsLimit = 1,
        historyDaysLimit = 1, historyDaysLimitKnown = true,
    )

    private fun ComposeUiTest.show(locale: String, content: @Composable () -> Unit) {
        setContent {
            customAppLocale = locale
            AppEnvironment { AppTheme { Column { content() } } }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.shows(vararg texts: String) {
        val missing = texts.filter { onAllNodes(hasText(it)).fetchSemanticsNodes().isEmpty() }
        assertTrue(missing.isEmpty(), "absents : $missing")
    }

    @Composable
    private fun row(projects: Long) =
        AdminUserRow(user(projects), isSelf = false, isProcessing = false, onRename = {}, onResetPassword = {}, onResendActivation = {}, onAssignPlan = {}, onDelete = {})

    @Test
    fun the_admin_user_list_says_1_projet_and_2_projets() = runComposeUiTest {
        show("fr") { row(1); row(2) }
        shows("1 projet", "2 projets")
    }

    @Test
    fun the_admin_user_list_says_1_project_in_english() = runComposeUiTest {
        show("en") { row(1); row(2) }
        shows("1 project", "2 projects")
    }

    @Test
    fun the_founder_offer_figures_agree_with_a_count_of_one() = runComposeUiTest {
        show("fr") {
            FounderOfferFigures(FounderOffer(projects = 1, photos = 1, videos = 1, supervisorsPerProject = 1, historyDays = 1))
            FounderOfferFigures(FounderOffer(videos = 1, videoMaxSeconds = 120))
        }
        shows("1 projet", "1 photo", "1 vidéo", "1 vidéo de 2:00 maximum", "1 superviseur par projet", "Historique conservé 1 jour")
    }

    @Test
    fun the_founder_offer_figures_agree_with_a_count_of_one_in_english() = runComposeUiTest {
        show("en") {
            FounderOfferFigures(FounderOffer(projects = 1, photos = 1, videos = 1, supervisorsPerProject = 1, historyDays = 1))
            FounderOfferFigures(FounderOffer(videos = 1, videoMaxSeconds = 120))
        }
        shows("1 project", "1 photo", "1 video", "1 video of up to 2:00", "1 supervisor per project", "History kept for 1 day")
    }

    @Test
    fun the_founder_offer_figures_stay_plural_above_one() = runComposeUiTest {
        show("fr") { FounderOfferFigures(FounderOffer(projects = 3, photos = 300, videos = 5, supervisorsPerProject = 2, historyDays = 45)) }
        shows("3 projets", "300 photos", "5 vidéos", "2 superviseurs par projet", "Historique conservé 45 jours")
    }

    @Test
    fun a_one_day_history_window_is_singular() = runComposeUiTest {
        show("fr") { Text(historyRetentionNotice(1)!!); Text(historyRetentionNotice(45)!!) }
        shows("Historique limité à 1 jour.", "Historique limité aux 45 derniers jours.")
    }

    @Test
    fun a_one_day_history_window_is_singular_in_english() = runComposeUiTest {
        show("en") { Text(historyRetentionNotice(1)!!) }
        shows("History is limited to 1 day.")
    }

    @Test
    fun the_billing_usage_agrees_with_a_limit_of_one() = runComposeUiTest {
        show("fr") { com.dmb.chantiertracker.presentation.billing.UsageSection(singleLimits) }
        shows("0/1 projet utilisé", "1/1 photo utilisée", "1/1 vidéo utilisée", "1/1 superviseur par projet")
    }

    @Test
    fun the_billing_usage_agrees_with_a_limit_of_one_in_english() = runComposeUiTest {
        show("en") { com.dmb.chantiertracker.presentation.billing.UsageSection(singleLimits) }
        shows("0/1 project used", "1/1 photo used", "1/1 video used", "1/1 supervisor per project")
    }

    @Test
    fun the_billing_usage_stays_plural_with_a_larger_limit() = runComposeUiTest {
        show("fr") { com.dmb.chantiertracker.presentation.billing.UsageSection(singleLimits.copy(projectsLimit = 3, supervisorsLimit = 3)) }
        shows("0/3 projets utilisés", "1/3 superviseurs par projet")
    }

    @Test
    fun my_offer_shows_a_one_day_history_as_1_jour() = runComposeUiTest {
        show("fr") { Text(com.dmb.chantiertracker.presentation.offer.historyValueLabel(1)); Text(com.dmb.chantiertracker.presentation.offer.historyValueLabel(45)) }
        shows("1 jour", "45 jours")
    }

    @Test
    fun my_offer_shows_a_one_day_history_as_1_day_in_english() = runComposeUiTest {
        show("en") { Text(com.dmb.chantiertracker.presentation.offer.historyValueLabel(1)); Text(com.dmb.chantiertracker.presentation.offer.historyValueLabel(45)) }
        shows("1 day", "45 days")
    }
}
