package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.UnsentWrites
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AppTopBar
import com.dmb.chantiertracker.presentation.main.DetailTopBar
import com.dmb.chantiertracker.presentation.main.LogoutPrompt
import com.dmb.chantiertracker.presentation.main.LogoutPromptDialog
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.sync.SyncIssuesScreen
import com.dmb.chantiertracker.presentation.sync.SyncIssuesViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeSyncIssueRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.issueItem
import com.dmb.chantiertracker.support.refusedIssue
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SyncIssuesSnapshotTest {

    private val outDir = File("build/auth-snapshots").apply { mkdirs() }

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun onDay(target: SyncIssueTarget, id: String, issue: SyncIssue, label: String? = null, quantity: Double? = null, serverQuantity: Double? = null, type: EntryType = EntryType.PURCHASE) =
        issueItem(
            target, id, issue, stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", date = "2026-10-09",
            entryType = type, label = label, unit = label?.let { "sac" }, quantity = quantity, serverQuantity = serverQuantity,
        )

    private val items = listOf(
        onDay(SyncIssueTarget.ENTRY, "e1", refusedIssue(RefusalReason.PROJECT_OR_STAGE_INACTIVE)),
        onDay(SyncIssueTarget.PURCHASE_LINE, "pl1", SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT), label = "Ciment", quantity = 3.0),
        onDay(SyncIssueTarget.PURCHASE_LINE, "pl2", refusedIssue(RefusalReason.STOCK_CONSUMED, kind = SyncIssueKind.UPDATE_REFUSED), label = "Sable", quantity = 3.0, serverQuantity = 10.0),
        issueItem(SyncIssueTarget.PROJECT, "p0", refusedIssue(RefusalReason.PLAN_LIMIT), projectLocalId = "p0", projectName = "Atelier Dupont"),
    )

    private fun capture(name: String, locale: String, width: Int, height: Int, content: @Composable () -> Unit) =
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                customAppLocale = locale
                AppEnvironment { AppTheme(darkTheme = false) { content() } }
            }
            waitForIdle()
            val file = File(outDir, "74-$name-$locale-$width.png")
            ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", file)
            assertTrue(file.length() > 0)
        }

    @Composable
    private fun ScreenWithChrome(title: String) {
        val vm = SyncIssuesViewModel(FakeSyncIssueRepository(items))
        Scaffold(topBar = { DetailTopBar(title = title, onBack = {}) }) { padding ->
            SyncIssuesScreen(modifier = Modifier.padding(padding), viewModel = vm)
        }
    }

    @Test
    fun capture_the_screen_on_a_small_phone_a_phone_a_tablet_and_a_desktop_window() {
        capture("sync-issues", "fr", 320, 1500) { ScreenWithChrome("Saisies à revoir") }
        capture("sync-issues", "fr", 412, 1400) { ScreenWithChrome("Saisies à revoir") }
        capture("sync-issues", "en", 412, 1400) { ScreenWithChrome("Entries to review") }
        capture("sync-issues", "fr", 800, 1280) { ScreenWithChrome("Saisies à revoir") }
        capture("sync-issues", "fr", 1440, 900) { ScreenWithChrome("Saisies à revoir") }
    }

    @Test
    fun capture_the_badge_and_the_banner_on_the_projects_list() {
        val projects = listOf(Project("1", "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS))
        for (width in listOf(320, 412)) {
            capture("projects-banner", "fr", width, 700) {
                val vm = ProjectsViewModel(FakeProjectRepository(projects = projects), FakeInvitationRepository(), ProjectSortHolder())
                Scaffold(
                    topBar = {
                        AppTopBar(
                            userName = "Jean Marchand", email = "jean@chantier.dev", plan = Plan.FREE, onSubscription = {}, onLogout = {},
                            syncIssueCount = 4, onOpenSyncIssues = {},
                        )
                    },
                ) { padding ->
                    ProjectsScreen(onProjectClick = {}, modifier = Modifier.padding(padding), viewModel = vm, syncIssueCount = 4, onOpenSyncIssues = {})
                }
            }
        }
    }

    @Test
    fun capture_the_sign_out_dialog_with_its_link_to_the_details() {
        capture("logout-details", "fr", 320, 640) {
            LogoutPromptDialog(LogoutPrompt.RefusedWritesLeft(UnsentWrites(entries = 1, lines = 2)), onRetry = {}, onLogoutAnyway = {}, onDismiss = {}, onSeeDetails = {})
        }
    }
}
