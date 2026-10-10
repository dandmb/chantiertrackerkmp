package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.repository.ConsumptionLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.repository.MaterialRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.PurchaseLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.repository.SyncIssueRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.SyncIssueTarget.ATTACHMENT
import com.dmb.chantiertracker.domain.model.SyncIssueTarget.CONSUMPTION_LINE
import com.dmb.chantiertracker.domain.model.SyncIssueTarget.ENTRY
import com.dmb.chantiertracker.domain.model.SyncIssueTarget.PROJECT
import com.dmb.chantiertracker.domain.model.SyncIssueTarget.PURCHASE_LINE
import com.dmb.chantiertracker.domain.model.SyncIssueTarget.STAGE
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.logs.DailyLogScreen
import com.dmb.chantiertracker.presentation.logs.DailyLogViewModel
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailViewModel
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkersProvider
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkersViewModel
import com.dmb.chantiertracker.presentation.sync.syncIssueMarkerTag
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.localAttachment
import com.dmb.chantiertracker.support.localConsumptionLine
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localMaterial
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localPurchaseLine
import com.dmb.chantiertracker.support.localStage
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SyncIssueMarkersCoherenceUiTest {

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val syncer = FakeSyncer()
    private val scope = AppCoroutineScope()
    private val fileStore = FakeAttachmentFileStore()
    private val issues = SyncIssueRepositoryImpl(
        db.syncIssueDao(), db.stageDao(), db.materialDao(), db.dailyEntryDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.attachmentDao(),
        syncer, db.syncIssueActionDao(), fileStore, FakeConnectivityObserver(initiallyOnline = false),
    )
    private val projects = ProjectRepositoryImpl(db.projectDao(), syncer, scope)
    private val stages = StageRepositoryImpl(db.stageDao(), syncer, scope)
    private val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), syncer, scope)
    private val materials = MaterialRepositoryImpl(db.materialDao(), db.purchaseLineDao(), db.consumptionLineDao(), db.stockDao(), syncer, scope)
    private val purchaseLines = PurchaseLineRepositoryImpl(db.purchaseLineDao(), syncer, scope)
    private val consumptionLines = ConsumptionLineRepositoryImpl(db.consumptionLineDao(), syncer, scope)
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), FakeProjectBackend().attachmentApi(), fileStore, syncer, scope)
    private val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(1, "jean@x.dev", "Jean", true, GlobalRole.USER))) }

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
        db.close()
    }

    private val synced = SyncStatus.SYNCED
    private val refused = SyncStatus.CONFLICTED
    private val none = PendingOp.NONE
    private val code = "PROJECT_INSUFFICIENT_ROLE"

    private fun aSiteWithEveryState() = runBlocking {
        db.projectDao().upsert(localProject("p", name = "Villa", serverId = 1, pendingOp = none, syncStatus = synced))
        db.projectDao().upsert(localProject("p-refused", name = "Refusé", syncStatus = refused, lastSyncError = SyncError.PLAN_LIMIT).copy(serverErrorCode = "PLAN_LIMIT_EXCEEDED"))
        db.projectDao().upsert(localProject("p-pending", name = "En cours d'envoi"))
        db.projectDao().upsert(localProject("p-awaiting", name = "Relu bientôt", serverId = 2, pendingOp = none, syncStatus = synced, lastSyncError = SyncError.AWAITING_SERVER_VERSION))
        db.projectDao().upsert(localProject("p-ghost", name = "Fantôme", serverId = 3, pendingOp = none, syncStatus = refused, lastSyncError = SyncError.DELETED_ON_SERVER))
        db.projectDao().upsert(localProject("p-delete-refused", name = "Suppression refusée", serverId = 4, pendingOp = none, syncStatus = synced, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = code))

        db.stageDao().upsert(localStage("st", projectLocalId = "p", name = "Charpente", serverId = 10, pendingOp = none, syncStatus = synced))
        db.stageDao().upsert(localStage("st-delete-refused", projectLocalId = "p", name = "Toiture", serverId = 11, pendingOp = none, syncStatus = synced, lastSyncError = SyncError.REJECTED).copy(serverErrorCode = code))
        db.stageDao().upsert(localStage("st-update-refused", projectLocalId = "p", name = "Bardage", serverId = 12, pendingOp = PendingOp.UPDATE, syncStatus = refused, lastSyncError = SyncError.UPDATE_REFUSED).copy(serverErrorCode = code))
        db.stageDao().upsert(localStage("st-pending", projectLocalId = "p", name = "Peinture"))
        db.stageDao().upsert(localStage("st-awaiting", projectLocalId = "p", name = "Isolation", serverId = 13, pendingOp = none, syncStatus = synced, lastSyncError = SyncError.AWAITING_SERVER_VERSION))
        db.stageDao().upsert(localStage("st-ghost", projectLocalId = "p", name = "Démolition", serverId = 14, pendingOp = none, syncStatus = refused, lastSyncError = SyncError.DELETED_ON_SERVER))
        db.stageDao().upsert(localStage("st-waiting", projectLocalId = "p-refused", name = "Sous un projet refusé"))

        db.materialDao().upsert(localMaterial("m", projectLocalId = "p", name = "Ciment", unit = "sac", serverId = 20, pendingOp = none, syncStatus = synced))
        db.materialDao().upsert(localMaterial("m-refused", projectLocalId = "p", name = "Gravier", unit = "t").copy(syncStatus = refused, lastSyncError = SyncError.REJECTED, serverErrorCode = "DUPLICATE_MATERIAL"))
        db.dailyLogDao().upsert(localDailyLog("l", stageLocalId = "st", date = "2026-09-05", serverId = 800))
        db.dailyEntryDao().upsert(localDailyEntry("e-purchase", dailyLogLocalId = "l", type = "PURCHASE", summary = "Livraison", serverId = 30, pendingOp = none, syncStatus = synced))
        db.dailyEntryDao().upsert(localDailyEntry("e-work", dailyLogLocalId = "l", type = "WORK", summary = "Coulage", serverId = 31, pendingOp = PendingOp.UPDATE, syncStatus = refused, lastSyncError = SyncError.UPDATE_REFUSED).copy(serverErrorCode = code))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-ok", entryLocalId = "e-purchase", materialLocalId = "m", serverId = 40, pendingOp = none, syncStatus = synced))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-refused", entryLocalId = "e-purchase", materialLocalId = "m").copy(syncStatus = refused, lastSyncError = SyncError.REJECTED, serverErrorCode = "VALIDATION_FAILED"))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-gone", entryLocalId = "e-purchase", materialLocalId = "m", serverId = 41, pendingOp = PendingOp.UPDATE, syncStatus = refused).copy(lastSyncError = SyncError.DELETED_ON_SERVER))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-waiting", entryLocalId = "e-purchase", materialLocalId = "m-refused"))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-pending", entryLocalId = "e-purchase", materialLocalId = "m"))
        db.purchaseLineDao().upsert(localPurchaseLine("pl-awaiting", entryLocalId = "e-purchase", materialLocalId = "m", serverId = 42, pendingOp = none, syncStatus = synced).copy(lastSyncError = SyncError.AWAITING_SERVER_VERSION))
        db.consumptionLineDao().upsert(localConsumptionLine("cl-ok", entryLocalId = "e-work", materialLocalId = "m", serverId = 50, pendingOp = none, syncStatus = synced))
        db.consumptionLineDao().upsert(
            localConsumptionLine("cl-change", entryLocalId = "e-work", materialLocalId = "m", quantity = 9.0, serverId = 51, pendingOp = PendingOp.UPDATE, syncStatus = refused)
                .copy(lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = "INSUFFICIENT_STOCK", serverQuantity = 2.0),
        )
        db.attachmentDao().upsert(localAttachment("a-ok", entryLocalId = "e-purchase", serverId = 60, pendingOp = none, syncStatus = synced))
        db.attachmentDao().upsert(localAttachment("a-refused", entryLocalId = "e-purchase").copy(syncStatus = refused, lastSyncError = SyncError.FILE_REFUSED, serverErrorCode = "ATTACHMENT_TOO_LARGE"))
        db.attachmentDao().upsert(localAttachment("a-pending", entryLocalId = "e-purchase"))
    }

    private val shownOnProjects = listOf("p", "p-refused", "p-pending", "p-awaiting", "p-ghost", "p-delete-refused").map { PROJECT to it }
    private val shownOnProject = listOf("st", "st-delete-refused", "st-update-refused", "st-pending", "st-awaiting", "st-ghost").map { STAGE to it }
    private val shownOnDay = listOf(ENTRY to "e-purchase", ENTRY to "e-work") +
        listOf("pl-ok", "pl-refused", "pl-gone", "pl-waiting", "pl-pending", "pl-awaiting").map { PURCHASE_LINE to it } +
        listOf(CONSUMPTION_LINE to "cl-ok", CONSUMPTION_LINE to "cl-change") +
        listOf("a-ok", "a-refused", "a-pending").map { ATTACHMENT to it }

    private fun ComposeUiTest.markerTags(): Set<String> {
        val tags = mutableSetOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("sync-marker:") }?.let { tags += it }
            node.children.forEach(::visit)
        }
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        return tags
    }

    private fun listedKeys() = runBlocking { issues.observeIssues().first() }.map { it.key }.toSet()

    private fun tags(vararg elements: Pair<SyncIssueTarget, String>) = elements.map { syncIssueMarkerTag(it.first, it.second) }.toSet()

    private fun ComposeUiTest.assertMarkedIfAndOnlyIfListed(shown: List<Pair<SyncIssueTarget, String>>, context: String): Set<String> {
        var expected = emptySet<String>()
        var seen = emptySet<String>()
        runCatching {
            waitUntil(timeoutMillis = 5_000L) {
                val listed = listedKeys()
                expected = shown.filter { "${it.first}:${it.second}" in listed }.map { syncIssueMarkerTag(it.first, it.second) }.toSet()
                seen = markerTags()
                seen == expected
            }
        }
        assertEquals(expected, seen, "$context: marked if and only if listed in the entries to review")
        return seen
    }

    @Test
    fun on_every_screen_an_element_is_marked_if_and_only_if_it_is_listed_to_review_and_stays_so_after_each_action() =
        runDesktopComposeUiTest(width = 412, height = 3000) {
            aSiteWithEveryState()
            var screen by mutableStateOf("projects")
            val markersViewModel = SyncIssueMarkersViewModel(issues)
            val projectsViewModel = ProjectsViewModel(projects, FakeInvitationRepository(), ProjectSortHolder())
            val projectViewModel = ProjectDetailViewModel(projects, stages, FakeInvitationRepository(), auth)
            val dayViewModel = DailyLogViewModel(logs, stages, projects, auth, materials, purchaseLines, consumptionLines, attachments)
            setContent {
                customAppLocale = "fr"
                AppEnvironment {
                    AppTheme {
                        Surface {
                            SyncIssueMarkersProvider(onOpen = {}, viewModel = markersViewModel) {
                                Box(Modifier.size(412.dp, 3000.dp)) {
                                    when (screen) {
                                        "projects" -> ProjectsScreen(onProjectClick = {}, viewModel = projectsViewModel)
                                        "project" -> ProjectDetailScreen("p", viewModel = projectViewModel)
                                        else -> DailyLogScreen(dailyLogLocalId = "l", viewModel = dayViewModel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fun awaitText(text: String) = waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

            awaitText("Fantôme")
            assertEquals(
                tags(PROJECT to "p-refused", PROJECT to "p-delete-refused"),
                assertMarkedIfAndOnlyIfListed(shownOnProjects, "projects"),
                "neither the pending project, nor the one awaiting its server version, nor the ghost parent",
            )

            screen = "project"
            awaitText("Démolition")
            assertEquals(tags(STAGE to "st-delete-refused", STAGE to "st-update-refused"), assertMarkedIfAndOnlyIfListed(shownOnProject, "project"))

            screen = "day"
            awaitText("Coulage")
            assertEquals(
                tags(ENTRY to "e-work", PURCHASE_LINE to "pl-refused", PURCHASE_LINE to "pl-gone", PURCHASE_LINE to "pl-waiting", CONSUMPTION_LINE to "cl-change", ATTACHMENT to "a-refused"),
                assertMarkedIfAndOnlyIfListed(shownOnDay, "day"),
            )

            val listed = runBlocking { issues.observeIssues().first() }
            val counted = runBlocking { issues.observeIssueCount().first() }
            assertEquals(listed.count { it.issue.kind != SyncIssueKind.BLOCKED_BY_PARENT }, counted, "the badge leaves out the waiting children, which are marked")
            assertEquals(listOf("pl-waiting", "st-waiting"), listed.filter { it.issue.kind == SyncIssueKind.BLOCKED_BY_PARENT }.map { it.localId }.sorted())
            assertEquals(listOf("m-refused"), listed.filter { it.target == SyncIssueTarget.MATERIAL }.map { it.localId }, "a material is listed, and no screen has a row to mark it on")

            runBlocking { issues.discard(listed.single { it.localId == "pl-refused" }) }
            assertEquals(
                tags(ENTRY to "e-work", PURCHASE_LINE to "pl-gone", PURCHASE_LINE to "pl-waiting", CONSUMPTION_LINE to "cl-change", ATTACHMENT to "a-refused"),
                assertMarkedIfAndOnlyIfListed(shownOnDay, "day after discard"),
            )
            runBlocking { issues.revert(listed.single { it.localId == "cl-change" }) }
            runBlocking { issues.acknowledge(listed.single { it.localId == "pl-gone" }) }
            runBlocking { issues.discard(listed.single { it.localId == "m-refused" }) }
            assertEquals(tags(ENTRY to "e-work", ATTACHMENT to "a-refused"), assertMarkedIfAndOnlyIfListed(shownOnDay, "day after undo, got it and discarding the material"))

            runBlocking {
                db.dailyEntryDao().upsert(db.dailyEntryDao().findByLocalId("e-work")!!.copy(syncStatus = synced, pendingOp = none, lastSyncError = null, serverErrorCode = null))
            }
            assertEquals(tags(ATTACHMENT to "a-refused"), assertMarkedIfAndOnlyIfListed(shownOnDay, "day after a pass wrote the entry as accepted"))

            screen = "project"
            awaitText("Démolition")
            runBlocking { issues.acknowledge(listed.single { it.localId == "st-delete-refused" }) }
            assertEquals(tags(STAGE to "st-update-refused"), assertMarkedIfAndOnlyIfListed(shownOnProject, "project after got it on the refused delete"))
            assertEquals(
                SyncError.AWAITING_SERVER_VERSION, runBlocking { db.stageDao().findByLocalId("st-delete-refused")!!.lastSyncError },
                "offline the stage now awaits its server version, and carries no marker",
            )
            assertTrue(markerTags().none { it.endsWith(":st-awaiting") || it.endsWith(":st-ghost") || it.endsWith(":st-pending") })
        }
}
