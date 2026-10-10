package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AppTopBar
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.sync.SyncIssueCountViewModel
import com.dmb.chantiertracker.presentation.sync.SyncIssuesScreen
import com.dmb.chantiertracker.presentation.sync.SyncIssuesViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AwaitedServerVersionIsSilentUiTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val syncer = FakeSyncer()
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        syncer, db.syncIssueActionDao(), FakeAttachmentFileStore(), FakeConnectivityObserver(initiallyOnline = false),
    )
    private val projects = ProjectRepositoryImpl(db.projectDao(), syncer, AppCoroutineScope())

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
        db.close()
    }

    private fun aRefusedDeleteOfAProjectAStageAndAnEntryAcknowledgedOffline() = runBlocking {
        val code = "PROJECT_INSUFFICIENT_ROLE"
        db.projectDao().upsert(localProject("p", name = "Villa Vidal", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = code))
        db.stageDao().upsert(localStage("st", projectLocalId = "p", name = "Charpente", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = code))
        db.dailyLogDao().upsert(localDailyLog("l", stageLocalId = "st", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e", dailyLogLocalId = "l", serverId = 3, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = code))
        val listed = issues.observeIssues().first()
        assertEquals(3, listed.size)
        listed.forEach { issues.acknowledge(it) }
        assertEquals(3, db.syncIssueActionDao().rowsAwaitingServerVersion().size)
    }

    private fun ComposeUiTest.assertNoErrorWording() {
        listOf("efus", "à revoir", "upprim", "AWAITING", "erreur").forEach { word ->
            onAllNodes(hasText(word, substring = true)).assertCountEquals(0)
            onAllNodesWithContentDescription(word, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun the_top_bar_shows_no_badge_and_the_projects_list_no_banner_and_no_error_on_the_project() = runDesktopComposeUiTest(width = 412, height = 900) {
        aRefusedDeleteOfAProjectAStageAndAnEntryAcknowledgedOffline()
        val counts = SyncIssueCountViewModel(issues)
        val list = ProjectsViewModel(projects, FakeInvitationRepository(), ProjectSortHolder())
        setContent {
            customAppLocale = "fr"
            val count by counts.count.collectAsState()
            AppEnvironment {
                AppTheme {
                    androidx.compose.foundation.layout.Column {
                        AppTopBar(
                            userName = "Jean Marchand", email = "jean@chantier.dev", plan = Plan.FREE, onSubscription = {}, onLogout = {},
                            syncIssueCount = count, onOpenSyncIssues = {},
                        )
                        ProjectsScreen(onProjectClick = {}, viewModel = list, syncIssueCount = count, onOpenSyncIssues = {})
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText("Villa Vidal").fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()

        assertEquals(0, counts.count.value)
        onAllNodesWithContentDescription("Saisies à revoir", substring = true).assertCountEquals(0)
        onAllNodesWithText("Le serveur n'a pas tout accepté.").assertCountEquals(0)
        assertNoErrorWording()
    }

    @Test
    fun the_screen_of_entries_to_review_is_empty_and_its_title_carries_no_count() = runDesktopComposeUiTest(width = 412, height = 900) {
        aRefusedDeleteOfAProjectAStageAndAnEntryAcknowledgedOffline()
        val vm = SyncIssuesViewModel(issues)
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { SyncIssuesScreen(viewModel = vm) } }
        }
        waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText("Rien à revoir").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Le serveur n'a refusé aucune de vos saisies.").assertExists()
        onAllNodes(hasText("élément", substring = true)).assertCountEquals(0)
        onAllNodesWithText("J'ai compris").assertCountEquals(0)
        assertEquals(0, vm.state.value.total)
        assertEquals(0, vm.state.value.listed)
    }
}
