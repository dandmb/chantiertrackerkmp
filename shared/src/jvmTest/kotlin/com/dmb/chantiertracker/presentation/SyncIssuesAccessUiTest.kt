package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.AppBottomBar
import com.dmb.chantiertracker.presentation.main.MainTab
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AppTopBar
import com.dmb.chantiertracker.presentation.main.LogoutPrompt
import com.dmb.chantiertracker.presentation.main.LogoutPromptDialog
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

@OptIn(ExperimentalTestApi::class)
class SyncIssuesAccessUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun topBar(count: Int, locale: String = "fr", width: Int = 412, block: ComposeUiTest.(clicks: () -> Int) -> Unit) =
        runDesktopComposeUiTest(width = width, height = 300) {
            var opened = 0
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme {
                        AppTopBar(
                            userName = "Jean Marchand", email = "jean@chantier.dev", plan = Plan.FREE, onSubscription = {}, onLogout = {},
                            syncIssueCount = count, onOpenSyncIssues = { opened++ },
                        )
                    }
                }
            }
            waitForIdle()
            block { opened }
        }

    @Test
    fun the_top_bar_shows_no_badge_when_there_is_nothing_to_review() = topBar(count = 0) {
        onAllNodesWithContentDescription("Saisies à revoir", substring = true).assertCountEquals(0)
    }

    @Test
    fun the_top_bar_badge_carries_the_count_reads_as_a_sentence_and_opens_the_screen() = topBar(count = 3) { opened ->
        onNodeWithText("3").assertExists()
        onNodeWithContentDescription("Saisies à revoir : 3 éléments").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened())
    }

    @Test
    fun the_top_bar_badge_is_worded_in_the_singular_and_in_english() {
        topBar(count = 1) { onNodeWithContentDescription("Saisies à revoir : 1 élément").assertExists() }
        topBar(count = 2, locale = "en") { onNodeWithContentDescription("Entries to review: 2 items").assertExists() }
    }

    @Test
    fun the_top_bar_badge_still_fits_next_to_the_account_menu_on_a_small_phone() = topBar(count = 120, width = 320) {
        val badge = onNodeWithContentDescription("Saisies à revoir : 120 éléments").assertIsDisplayed().getUnclippedBoundsInRoot()
        val menu = onNodeWithContentDescription("Menu du compte").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(badge.right <= menu.left && menu.right <= 320.dp, "badge then account menu, both inside 320 dp: $badge / $menu")
        onNodeWithText("99+").assertExists()
    }

    private val villa = Project("p1", "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")

    private fun projectsList(count: Int, locale: String = "fr", width: Int = 412, projects: List<Project> = listOf(villa), block: ComposeUiTest.(clicks: () -> Int) -> Unit) =
        runDesktopComposeUiTest(width = width, height = 800) {
            var opened = 0
            val vm = ProjectsViewModel(FakeProjectRepository(projects = projects), FakeInvitationRepository(), ProjectSortHolder())
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme { ProjectsScreen(onProjectClick = {}, viewModel = vm, syncIssueCount = count, onOpenSyncIssues = { opened++ }) }
                }
            }
            waitForIdle()
            block { opened }
        }

    @Test
    fun the_projects_list_shows_no_banner_when_there_is_nothing_to_review() = projectsList(count = 0) {
        onNodeWithText("Villa Vidal").assertExists()
        onAllNodesWithText("Voir le détail").assertCountEquals(0)
    }

    @Test
    fun the_projects_list_banner_names_the_count_and_opens_the_screen() = projectsList(count = 2) { opened ->
        onNodeWithText("2 éléments à revoir").assertIsDisplayed()
        onNodeWithText("Le serveur n'a pas tout accepté.").assertIsDisplayed()
        onNodeWithText("Villa Vidal").assertIsDisplayed()
        onNodeWithText("Voir le détail").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened())
    }

    @Test
    fun the_banner_stays_while_the_projects_list_is_empty_and_is_translated() {
        projectsList(count = 1, projects = emptyList()) { onNodeWithText("1 élément à revoir").assertIsDisplayed() }
        projectsList(count = 2, locale = "en") {
            onNodeWithText("2 items to review").assertIsDisplayed()
            onNodeWithText("The server did not accept everything.").assertIsDisplayed()
            onNodeWithText("See details").assertIsDisplayed()
        }
    }

    @Test
    fun the_banner_fits_on_a_small_phone_and_is_capped_on_a_desktop_window() {
        projectsList(count = 2, width = 320) {
            val button = onNodeWithText("Voir le détail").assertIsDisplayed().getUnclippedBoundsInRoot()
            val title = onNodeWithText("2 éléments à revoir").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(title.left >= 0.dp && button.right <= 320.dp, "inside 320 dp: $title / $button")
        }
        projectsList(count = 2, width = 1440) {
            val button = onNodeWithText("Voir le détail").assertIsDisplayed().getUnclippedBoundsInRoot()
            val title = onNodeWithText("2 éléments à revoir").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(title.left >= 400.dp && button.right <= 1040.dp, "inside the 640 dp band: $title / $button")
        }
    }

    private fun logoutDialog(prompt: LogoutPrompt, locale: String = "fr", width: Int = 412, block: ComposeUiTest.(clicks: () -> Int) -> Unit) =
        runDesktopComposeUiTest(width = width, height = 800) {
            var details = 0
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme {
                        LogoutPromptDialog(prompt, onRetry = {}, onLogoutAnyway = {}, onDismiss = {}, onSeeDetails = { details++ })
                    }
                }
            }
            waitForIdle()
            block { details }
        }

    @Test
    fun the_sign_out_dialog_about_refused_entries_offers_to_see_the_details() =
        logoutDialog(LogoutPrompt.RefusedWritesLeft(UnsentWrites(entries = 1, lines = 2))) { details ->
            onNodeWithText("Se déconnecter quand même").assertExists()
            onNodeWithText("Voir le détail").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(1, details())
        }

    @Test
    fun the_sign_out_dialog_keeps_its_three_actions_on_a_small_phone_and_in_english() {
        logoutDialog(LogoutPrompt.RefusedWritesLeft(UnsentWrites(entries = 1)), width = 320) {
            listOf("Voir le détail", "Annuler", "Se déconnecter quand même").forEach { label ->
                val bounds = onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= 0.dp && bounds.right <= 320.dp, "« $label » fits in 320 dp: $bounds")
            }
        }
        logoutDialog(LogoutPrompt.RefusedWritesLeft(UnsentWrites(entries = 1)), locale = "en") {
            onNodeWithText("See details").assertIsDisplayed()
        }
    }

    private fun ComposeUiTest.everythingShown(): List<String> {
        val texts = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { texts += it }
            node.children.forEach(::visit)
        }
        onAllNodes(isRoot()).fetchSemanticsNodes().forEach { root -> visit(root) }
        return texts
    }

    private val looksLikeACode = Regex("[A-Z]{2,}_[A-Z_]{2,}")

    private fun ComposeUiTest.assertNoServerCodeIsShown(where: String, atLeast: Int) {
        val shown = everythingShown()
        assertTrue(shown.size >= atLeast, "$where: the texts and descriptions were read (${shown.size})")
        assertTrue(shown.none { looksLikeACode.containsMatchIn(it) }, "$where shows nothing shaped like a server code: ${shown.filter { looksLikeACode.containsMatchIn(it) }}")
    }

    @Test
    fun the_badge_the_banner_and_the_sign_out_dialog_never_show_a_server_code_in_text_or_description() {
        for (locale in listOf("fr", "en")) {
            topBar(count = 3, locale = locale) { assertNoServerCodeIsShown("top bar badge ($locale)", atLeast = 3) }
            projectsList(count = 3, locale = locale) { assertNoServerCodeIsShown("projects banner ($locale)", atLeast = 4) }
            logoutDialog(LogoutPrompt.RefusedWritesLeft(UnsentWrites(projects = 1, stages = 1, materials = 1, entries = 1, lines = 2, attachments = 1)), locale = locale) {
                assertNoServerCodeIsShown("sign-out dialog ($locale)", atLeast = 5)
            }
        }
    }

    @Test
    fun at_200_percent_font_on_an_iphone_se_the_banner_hides_no_action_and_the_list_still_scrolls() =
        runDesktopComposeUiTest(width = 320, height = 568) {
            var opened = 0
            var created = 0
            var clickedProject: String? = null
            val projects = (1..6).map { Project("p$it", "Chantier $it", null, null, ProjectStatus.IN_PROGRESS, createdAt = "2026-01-0${it}T09:00:00") }
            val vm = ProjectsViewModel(FakeProjectRepository(projects = projects), FakeInvitationRepository(), ProjectSortHolder())
            setContent {
                customAppLocale = "fr"
                AppEnvironment {
                    AppTheme {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                            Scaffold(
                                topBar = {
                                    AppTopBar(
                                        userName = "Jean Marchand", email = "jean@chantier.dev", plan = Plan.FREE, onSubscription = {}, onLogout = {},
                                        syncIssueCount = 12, onOpenSyncIssues = { opened++ },
                                    )
                                },
                                bottomBar = { AppBottomBar(current = MainTab.Projects, tabs = listOf(MainTab.Projects, MainTab.Settings), onSelect = {}) },
                                floatingActionButton = {
                                    FloatingActionButton(onClick = { created++ }) { Icon(AddIcon, contentDescription = "Nouveau projet") }
                                },
                            ) { padding ->
                                ProjectsScreen(
                                    onProjectClick = { clickedProject = it }, modifier = Modifier.padding(padding), viewModel = vm,
                                    syncIssueCount = 12, onOpenSyncIssues = { opened++ },
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()

            val details = onNodeWithText("Voir le détail").assertIsDisplayed().getUnclippedBoundsInRoot()
            val fab = onNodeWithContentDescription("Nouveau projet").assertIsDisplayed().getUnclippedBoundsInRoot()
            val badge = onNodeWithContentDescription("Saisies à revoir : 12 éléments").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(details.right <= 320.dp && details.bottom <= fab.top || details.right <= fab.left, "the create button does not cover « Voir le détail »: $details / $fab")
            assertTrue(badge.right <= 320.dp && badge.left >= 0.dp)
            listOf("12 éléments à revoir", "Le serveur n'a pas tout accepté.", "Voir le détail").forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                assertTrue(layouts.isNotEmpty() && layouts.none { it.isCut() }, "« $text » is not cut")
            }

            onNodeWithText("Voir le détail").performClick()
            onNodeWithContentDescription("Saisies à revoir : 12 éléments").performClick()
            onNodeWithContentDescription("Nouveau projet").performClick()
            assertEquals(listOf(2, 1), listOf(opened, created))

            onNode(hasScrollAction()).performScrollToNode(hasText("Chantier 1"))
            waitForIdle()
            onNodeWithText("Chantier 1").assertIsDisplayed().performClick()
            assertEquals("p1", clickedProject, "below the banner the list still scrolls to its last project and opens it")
        }

    @Test
    fun at_200_percent_font_on_an_iphone_se_the_sign_out_dialog_keeps_every_action_reachable() =
        runDesktopComposeUiTest(width = 320, height = 568) {
            val clicked = mutableListOf<String>()
            setContent {
                customAppLocale = "fr"
                AppEnvironment {
                    AppTheme {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                            LogoutPromptDialog(
                                LogoutPrompt.RefusedWritesLeft(UnsentWrites(entries = 1, lines = 2)),
                                onRetry = {}, onLogoutAnyway = { clicked += "anyway" }, onDismiss = { clicked += "cancel" }, onSeeDetails = { clicked += "details" },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            listOf("Voir le détail" to "details", "Annuler" to "cancel", "Se déconnecter quand même" to "anyway").forEach { (label, action) ->
                if (action == "details") onNodeWithText(label).performScrollTo()
                waitForIdle()
                val bounds = onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= 0.dp && bounds.right <= 320.dp && bounds.top >= 0.dp && bounds.bottom <= 568.dp, "« $label » is on a 320 x 568 screen: $bounds")
                onNodeWithText(label).performClick()
                assertEquals(action, clicked.last())
            }
        }

    @Test
    fun the_sign_out_dialog_about_entries_simply_not_sent_yet_has_no_details_link() =
        logoutDialog(LogoutPrompt.Blocked(UnsentWrites(entries = 1))) {
            onAllNodesWithText("Voir le détail").assertCountEquals(0)
        }
}
