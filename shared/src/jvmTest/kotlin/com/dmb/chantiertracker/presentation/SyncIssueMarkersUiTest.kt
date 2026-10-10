package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
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
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarker
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkerStyle
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkersProvider
import com.dmb.chantiertracker.presentation.sync.SyncIssueMarkersViewModel
import com.dmb.chantiertracker.presentation.sync.markerIcon
import com.dmb.chantiertracker.presentation.sync.syncIssueMarkerTag
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAttachmentRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeConsumptionLineRepository
import com.dmb.chantiertracker.support.FakeDailyLogRepository
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeMaterialRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakePurchaseLineRepository
import com.dmb.chantiertracker.support.FakeStageRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun TextLayoutResult.isCutInMarker(): Boolean =
    didOverflowHeight ||
        getLineEnd(lineCount - 1, visibleEnd = true) < layoutInput.text.length ||
        (0 until lineCount).any { line -> getLineRight(line) > size.width + 1.5f || isLineEllipsized(line) }

@OptIn(ExperimentalTestApi::class)
class SyncIssueMarkersUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val french = mapOf(
        SyncIssueKind.REFUSED to "Refusé par le serveur",
        SyncIssueKind.UPDATE_REFUSED to "Modification refusée",
        SyncIssueKind.DELETE_REFUSED to "Suppression refusée",
        SyncIssueKind.DELETED_ON_SERVER to "Supprimé sur le serveur",
        SyncIssueKind.BLOCKED_BY_PARENT to "En attente",
    )
    private val english = mapOf(
        SyncIssueKind.REFUSED to "Refused by the server",
        SyncIssueKind.UPDATE_REFUSED to "Change refused",
        SyncIssueKind.DELETE_REFUSED to "Deletion refused",
        SyncIssueKind.DELETED_ON_SERVER to "Deleted on the server",
        SyncIssueKind.BLOCKED_BY_PARENT to "Waiting",
    )

    private fun issueOf(kind: SyncIssueKind) = when (kind) {
        SyncIssueKind.DELETED_ON_SERVER, SyncIssueKind.BLOCKED_BY_PARENT -> SyncIssue(kind)
        else -> refusedIssue(RefusalReason.INSUFFICIENT_ROLE, serverCode = "PROJECT_INSUFFICIENT_ROLE", kind = kind)
    }

    private fun stageItem(kind: SyncIssueKind, id: String = "st-${kind.name}") =
        issueItem(SyncIssueTarget.STAGE, id, issueOf(kind), stageLocalId = id, stageName = "Charpente")

    private fun tag(item: SyncIssueItem) = syncIssueMarkerTag(item.target, item.localId)

    private class Opened {
        val items = mutableListOf<SyncIssueItem>()
    }

    @Composable
    private fun Environment(locale: String, fontScale: Float, repo: FakeSyncIssueRepository, opened: Opened, content: @Composable () -> Unit) {
        customAppLocale = locale
        val markers = remember(repo) { SyncIssueMarkersViewModel(repo) }
        AppEnvironment {
            AppTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface { SyncIssueMarkersProvider(onOpen = { opened.items += it }, viewModel = markers, content = content) }
                }
            }
        }
    }

    private fun onMarkers(
        items: List<SyncIssueItem>,
        shown: List<Pair<SyncIssueTarget, String>> = items.map { it.target to it.localId },
        style: SyncIssueMarkerStyle = SyncIssueMarkerStyle.LABELLED,
        locale: String = "fr",
        block: ComposeUiTest.(FakeSyncIssueRepository, Opened) -> Unit,
    ) = runDesktopComposeUiTest(width = 412, height = 900) {
        val repo = FakeSyncIssueRepository(items)
        val opened = Opened()
        setContent {
            Environment(locale, 1f, repo, opened) {
                Column { shown.forEach { (target, id) -> SyncIssueMarker(target, id, style = style) } }
            }
        }
        waitForIdle()
        block(repo, opened)
    }

    private fun ComposeUiTest.awaitMarker(tag: String) {
        runCatching { waitUntil(timeoutMillis = 3_000L) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        assertTrue(onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty(), "the marker $tag is shown")
    }

    private fun ComposeUiTest.awaitNoMarker(tag: String) {
        runCatching { waitUntil(timeoutMillis = 3_000L) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() } }
        assertTrue(onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(), "the marker $tag is gone")
    }

    private fun ComposeUiTest.markerTags(): Set<String> {
        val tags = mutableSetOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("sync-marker:") }?.let { tags += it }
            node.children.forEach(::visit)
        }
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        return tags
    }

    private fun iconTag(kind: SyncIssueKind) = "sync-marker-icon:" + when (kind) {
        SyncIssueKind.DELETED_ON_SERVER -> "SyncDeletedOnServer"
        SyncIssueKind.BLOCKED_BY_PARENT -> "SyncWaiting"
        else -> "SyncRefused"
    }

    private fun ComposeUiTest.iconTagsUnder(markerTag: String): List<String> {
        val tags = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith("sync-marker-icon:") }?.let { tags += it }
            node.children.forEach(::visit)
        }
        visit(onNodeWithTag(markerTag, useUnmergedTree = true).fetchSemanticsNode())
        return tags
    }

    private fun ComposeUiTest.everythingShown(): List<String> {
        val texts = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { texts += it }
            node.config.getOrNull(SemanticsActions.OnClick)?.label?.let { texts += it }
            node.children.forEach(::visit)
        }
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        return texts
    }

    @Test
    fun each_state_has_its_own_marker_with_a_label_and_a_spoken_action_in_both_languages() {
        val items = SyncIssueKind.entries.map { stageItem(it) }
        listOf("fr" to french, "en" to english).forEach { (locale, labels) ->
            onMarkers(items, locale = locale) { _, _ ->
                items.forEach { item ->
                    awaitMarker(tag(item))
                    val node = onNodeWithTag(tag(item)).assertIsDisplayed().fetchSemanticsNode()
                    assertEquals(listOf(labels.getValue(item.issue.kind)), node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }, "$locale ${item.issue.kind}")
                    assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
                    assertEquals(listOf(iconTag(item.issue.kind)), iconTagsUnder(tag(item)), "$locale ${item.issue.kind}: the label comes with the icon of its state")
                    assertEquals(
                        if (locale == "fr") "Voir dans Saisies à revoir" else "See in Entries to review",
                        node.config.getOrNull(SemanticsActions.OnClick)?.label,
                        "a screen reader says where the tap leads",
                    )
                }
                assertEquals(5, labels.values.toSet().size)
            }
        }
    }

    @Test
    fun a_refusal_an_element_deleted_on_the_server_and_a_waiting_child_never_share_an_icon() {
        val refused = SyncIssueKind.REFUSED.markerIcon()
        val gone = SyncIssueKind.DELETED_ON_SERVER.markerIcon()
        val waiting = SyncIssueKind.BLOCKED_BY_PARENT.markerIcon()

        assertEquals(3, setOf(refused.name, gone.name, waiting.name).size, "three shapes, so the state does not rest on the colour")
        assertNotEquals(refused, gone)
        assertNotEquals(refused, waiting)
        assertNotEquals(gone, waiting)
        assertEquals(refused, SyncIssueKind.UPDATE_REFUSED.markerIcon())
        assertEquals(refused, SyncIssueKind.DELETE_REFUSED.markerIcon())
    }

    @Test
    fun the_icon_only_marker_of_a_thumbnail_still_says_its_state_and_its_action() {
        val items = SyncIssueKind.entries.map { issueItem(SyncIssueTarget.ATTACHMENT, "a-${it.name}", issueOf(it), label = "facture.jpg") }
        onMarkers(items, style = SyncIssueMarkerStyle.ICON_ONLY) { _, _ ->
            items.forEach { item ->
                awaitMarker(tag(item))
                val node = onNodeWithTag(tag(item)).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
                assertEquals(listOf(french.getValue(item.issue.kind)), node.config.getOrNull(SemanticsProperties.ContentDescription))
                assertEquals("Voir dans Saisies à revoir", node.config.getOrNull(SemanticsActions.OnClick)?.label)
                assertEquals(listOf(iconTag(item.issue.kind)), iconTagsUnder(tag(item)))
            }
        }
    }

    @Test
    fun an_element_that_is_not_to_review_has_no_marker() = onMarkers(
        items = listOf(stageItem(SyncIssueKind.REFUSED, id = "st-refused")),
        shown = listOf(SyncIssueTarget.STAGE to "st-refused", SyncIssueTarget.STAGE to "st-fine", SyncIssueTarget.ENTRY to "st-refused"),
    ) { _, _ ->
        awaitMarker(syncIssueMarkerTag(SyncIssueTarget.STAGE, "st-refused"))
        assertEquals(setOf(syncIssueMarkerTag(SyncIssueTarget.STAGE, "st-refused")), markerTags(), "the same id on another kind of element is another element")
        onAllNodesWithText("Refusé par le serveur").assertCountEquals(1)
    }

    @Test
    fun touching_a_marker_opens_that_very_entry_and_the_target_is_48_dp() {
        val refused = stageItem(SyncIssueKind.REFUSED)
        val waiting = stageItem(SyncIssueKind.BLOCKED_BY_PARENT)
        onMarkers(listOf(refused, waiting)) { _, opened ->
            awaitMarker(tag(waiting))
            onNodeWithTag(tag(waiting)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            onNodeWithTag(tag(refused)).assertHeightIsAtLeast(48.dp).performClick()
            waitForIdle()

            assertEquals(listOf(waiting.key, refused.key), opened.items.map { it.key })
        }
    }

    @Test
    fun markers_follow_the_list_after_an_action_or_a_sync_without_relaunching_the_screen() {
        val refused = stageItem(SyncIssueKind.REFUSED, id = "st1")
        val other = stageItem(SyncIssueKind.DELETE_REFUSED, id = "st2")
        onMarkers(listOf(refused), shown = listOf(SyncIssueTarget.STAGE to "st1", SyncIssueTarget.STAGE to "st2")) { repo, _ ->
            awaitMarker(tag(refused))
            assertEquals(setOf(tag(refused)), markerTags())

            repo.items.value = listOf(stageItem(SyncIssueKind.UPDATE_REFUSED, id = "st1"), other)
            runCatching { waitUntil(timeoutMillis = 3_000L) { onAllNodesWithText("Modification refusée").fetchSemanticsNodes().isNotEmpty() } }
            onAllNodesWithText("Modification refusée").assertCountEquals(1)
            onAllNodesWithText("Refusé par le serveur").assertCountEquals(0)
            onNodeWithText("Suppression refusée").assertIsDisplayed()

            repo.items.value = listOf(other)
            awaitNoMarker(tag(refused))
            assertEquals(setOf(tag(other)), markerTags())

            repo.items.value = emptyList()
            awaitNoMarker(tag(other))
            assertTrue(markerTags().isEmpty())
        }
    }

    @Test
    fun no_server_code_is_shown_or_spoken_by_a_marker() {
        val looksLikeACode = Regex("[A-Z]{2,}_[A-Z_]{2,}")
        val items = SyncIssueKind.entries.map { stageItem(it) } +
            issueItem(SyncIssueTarget.PROJECT, "p-unknown", refusedIssue(RefusalReason.UNKNOWN, serverCode = "A_CODE_FROM_A_NEWER_SERVER"))
        listOf(SyncIssueMarkerStyle.LABELLED, SyncIssueMarkerStyle.ICON_ONLY).forEach { style ->
            listOf("fr", "en").forEach { locale ->
                onMarkers(items, style = style, locale = locale) { _, _ ->
                    awaitMarker(tag(items.last()))
                    val shown = everythingShown()
                    assertTrue(shown.size >= 6, "$shown")
                    assertTrue(shown.none { looksLikeACode.containsMatchIn(it) }, "$style $locale: ${shown.filter { looksLikeACode.containsMatchIn(it) }}")
                }
            }
        }
    }

    private val admin = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(1, "jean@x.dev", "Jean", true, GlobalRole.USER))) }
    private val villa = Project("p1", "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")
    private val atelier = Project("p2", "Atelier", null, "Alès", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-02T09:00:00")
    private val villaDetail = ProjectDetail(
        localId = "p1", name = "Villa Vidal", description = null, location = "Nîmes", currency = "EUR", timezone = "Europe/Paris",
        status = ProjectStatus.IN_PROGRESS, ownerId = 1L, ownerPlan = Plan.SEMI_FLEX,
    )
    private val stages = listOf(
        Stage("st1", "p1", "Charpente et couverture", 12000.0, StageStatus.IN_PROGRESS),
        Stage("st2", "p1", "Gros œuvre", null, StageStatus.COMPLETED),
    )

    private fun tempPng(): String {
        val file = File.createTempFile("marker-", ".png").apply { deleteOnExit() }
        val image = java.awt.image.BufferedImage(48, 48, java.awt.image.BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "png", file)
        return file.absolutePath
    }

    private fun dayViewModel(): DailyLogViewModel {
        val logs = FakeDailyLogRepository(
            detail = DailyLogDetail(
                localId = "log-1", stageLocalId = "st1", date = "2026-09-05",
                entries = listOf(DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "Livraison"), DailyEntry("e2", "log-1", EntryType.WORK, summary = "Coulage")),
            ),
        )
        val stageRepo = FakeStageRepository(detail = StageDetail("st1", "p1", "Charpente", null, null, null, null, StageStatus.IN_PROGRESS))
        val materials = FakeMaterialRepository(
            materials = listOf(Material("mat-cement", "p1", "Ciment", "sac"), Material("mat-sand", "p1", "Sable", "kg")),
            stock = listOf(MaterialStock("mat-sand", "Sable", "kg", quantityIn = 500.0, quantityOut = 0.0)),
        )
        val purchaseLines = FakePurchaseLineRepository(
            listOf(PurchaseLine("pl1", "e1", "mat-cement", 10.0, 5.0, 50.0, supplier = "Point P"), PurchaseLine("pl2", "e1", "mat-sand", 3.0, 2.0, 6.0, supplier = null)),
        )
        val consumptionLines = FakeConsumptionLineRepository(listOf(ConsumptionLine("cl1", "e2", "mat-sand", 20.0)))
        val attachments = FakeAttachmentRepository(
            listOf(
                Attachment("a1", "e1", tempPng(), "recu.jpg", "image/jpeg", 1L, uploadedAt = 0L),
                Attachment("a2", "e1", tempPng(), "bon.jpg", "image/jpeg", 1L, uploadedAt = 0L),
            ),
        )
        return DailyLogViewModel(logs, stageRepo, FakeProjectRepository(detail = villaDetail), admin, materials, purchaseLines, consumptionLines, attachments)
    }

    private val onDay = listOf(
        issueItem(SyncIssueTarget.ENTRY, "e1", issueOf(SyncIssueKind.REFUSED), entryType = EntryType.PURCHASE),
        issueItem(SyncIssueTarget.ENTRY, "e2", issueOf(SyncIssueKind.DELETED_ON_SERVER), entryType = EntryType.WORK),
        issueItem(SyncIssueTarget.PURCHASE_LINE, "pl1", issueOf(SyncIssueKind.BLOCKED_BY_PARENT), label = "Ciment"),
        issueItem(SyncIssueTarget.CONSUMPTION_LINE, "cl1", issueOf(SyncIssueKind.UPDATE_REFUSED), label = "Sable"),
        issueItem(SyncIssueTarget.ATTACHMENT, "a1", refusedIssue(RefusalReason.FILE_REFUSED, serverCode = "ATTACHMENT_TOO_LARGE"), label = "recu.jpg"),
    )

    private enum class Screen { PROJECTS, PROJECT, DAY }

    private class Taps {
        var projects = 0
        var stages = 0
    }

    private fun onScreen(
        screen: Screen,
        items: List<SyncIssueItem>,
        width: Int = 412,
        height: Int = 1600,
        fontScale: Float = 1f,
        block: ComposeUiTest.(FakeSyncIssueRepository, Opened, Taps) -> Unit,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val repo = FakeSyncIssueRepository(items)
        val opened = Opened()
        val taps = Taps()
        val projectsViewModel = ProjectsViewModel(FakeProjectRepository(projects = listOf(villa, atelier)), FakeInvitationRepository(), ProjectSortHolder())
        val projectViewModel = ProjectDetailViewModel(
            FakeProjectRepository(detail = villaDetail.copy(ownerPlan = null), members = listOf(ProjectMember(userId = 1, name = "Jean", email = "jean@x.dev", role = ProjectRole.ADMIN))),
            FakeStageRepository(stages = stages), FakeInvitationRepository(), admin,
        )
        val dayViewModel = dayViewModel()
        setContent {
            Environment("fr", fontScale, repo, opened) {
                Box(Modifier.size(width.dp, height.dp)) {
                    when (screen) {
                        Screen.PROJECTS -> ProjectsScreen(onProjectClick = { taps.projects++ }, viewModel = projectsViewModel)
                        Screen.PROJECT -> ProjectDetailScreen("p1", viewModel = projectViewModel, onStageClick = { taps.stages++ })
                        Screen.DAY -> DailyLogScreen(dailyLogLocalId = "log-1", viewModel = dayViewModel)
                    }
                }
            }
        }
        waitForIdle()
        block(repo, opened, taps)
    }

    @Test
    fun a_project_card_carries_the_marker_of_its_project_and_touching_it_opens_the_review_not_the_project() {
        val refusedProject = issueItem(SyncIssueTarget.PROJECT, "p1", issueOf(SyncIssueKind.REFUSED))
        onScreen(Screen.PROJECTS, listOf(refusedProject)) { _, opened, taps ->
            awaitMarker(tag(refusedProject))
            assertEquals(setOf(tag(refusedProject)), markerTags(), "the other project has none")
            onNodeWithText("Refusé par le serveur").assertIsDisplayed()

            onNodeWithTag(tag(refusedProject)).performClick()
            waitForIdle()
            assertEquals(listOf(refusedProject.key), opened.items.map { it.key })
            assertEquals(0, taps.projects)

            onNodeWithText("Villa Vidal").performClick()
            waitForIdle()
            assertEquals(1, taps.projects, "the rest of the card still opens the project")
        }
    }

    @Test
    fun a_stage_row_carries_the_marker_of_its_stage() {
        val goneStage = issueItem(SyncIssueTarget.STAGE, "st2", issueOf(SyncIssueKind.DELETED_ON_SERVER), stageLocalId = "st2", stageName = "Gros œuvre")
        onScreen(Screen.PROJECT, listOf(goneStage)) { _, opened, taps ->
            awaitMarker(tag(goneStage))
            assertEquals(setOf(tag(goneStage)), markerTags())
            onNodeWithTag(tag(goneStage)).performScrollTo().assertIsDisplayed().performClick()
            waitForIdle()

            assertEquals(listOf(goneStage.key), opened.items.map { it.key })
            assertEquals(0, taps.stages)
        }
    }

    @Test
    fun on_a_day_the_entries_the_lines_and_the_thumbnails_each_carry_their_own_marker() = onScreen(Screen.DAY, onDay) { _, opened, _ ->
        onDay.forEach { awaitMarker(tag(it)) }
        assertEquals(onDay.map { tag(it) }.toSet(), markerTags(), "the second purchase line and the second photo have none")

        fun stateOf(item: SyncIssueItem): List<String> {
            val config = onNodeWithTag(tag(item)).fetchSemanticsNode().config
            return config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
        assertEquals(
            listOf("Refusé par le serveur", "Supprimé sur le serveur", "En attente", "Modification refusée", "Refusé par le serveur"),
            onDay.map { stateOf(it).single() },
        )
        onDay.forEach { onNodeWithTag(tag(it)).performScrollTo().performClick() }
        waitForIdle()
        assertEquals(onDay.map { it.key }, opened.items.map { it.key })
    }

    private fun ComposeUiTest.assertMarkerIsWholeAndAlone(tag: String, width: Int, context: String) {
        onNodeWithTag(tag).performScrollTo()
        waitForIdle()
        val marker = onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val bounds = Rect(marker.positionInRoot, androidx.compose.ui.geometry.Size(marker.size.width.toFloat(), marker.size.height.toFloat()))
        assertEquals(bounds, marker.boundsInRoot, "$context: nothing of $tag is clipped away")
        assertTrue(bounds.left >= 0f && bounds.right <= width * density.density + 0.5f, "$context: $tag is inside the screen: $bounds")
        assertTrue(bounds.height >= 48 * density.density - 0.5f, "$context: $tag keeps a 48 dp target")

        val own = mutableSetOf<Int>()
        fun collect(node: SemanticsNode) { own += node.id; node.children.forEach(::collect) }
        collect(marker)
        fun visitOwn(node: SemanticsNode) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            assertTrue(layouts.none { it.isCutInMarker() }, "$context: the label of $tag is not cut")
            node.children.forEach(::visitOwn)
        }
        visitOwn(marker)

        fun Rect.touches(other: Rect) = left < other.right - 0.5f && other.left < right - 0.5f && top < other.bottom - 0.5f && other.top < bottom - 0.5f
        fun visit(node: SemanticsNode) {
            if (node.id in own) return
            val carriesSomething = node.config.contains(SemanticsProperties.Text) || node.config.contains(SemanticsProperties.ContentDescription) ||
                (node.config.contains(SemanticsActions.OnClick) && node.children.isEmpty())
            val other = node.boundsInRoot
            if (carriesSomething && !other.isEmpty) {
                assertTrue(!bounds.touches(other), "$context: $tag $bounds overlaps « ${node.config.getOrNull(SemanticsProperties.Text) ?: node.config.getOrNull(SemanticsProperties.ContentDescription)} » $other")
            }
            node.children.forEach(::visit)
        }
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
    }

    @Test
    fun at_200_percent_font_on_a_320_dp_phone_and_on_an_iphone_se_no_marker_is_cut_or_overlaps_anything() {
        val everyKindOnProjects = SyncIssueKind.entries.map { kind -> issueItem(SyncIssueTarget.PROJECT, "p1", issueOf(kind)) }
        val everyKindOnStages = SyncIssueKind.entries.map { kind -> issueItem(SyncIssueTarget.STAGE, "st1", issueOf(kind), stageLocalId = "st1", stageName = "Charpente et couverture") }
        listOf(320 to 640, 320 to 568, 375 to 667).forEach { (width, height) ->
            everyKindOnProjects.forEach { item ->
                onScreen(Screen.PROJECTS, listOf(item, issueItem(SyncIssueTarget.PROJECT, "p2", issueOf(SyncIssueKind.UPDATE_REFUSED))), width, height, fontScale = 2f) { _, _, _ ->
                    awaitMarker(tag(item))
                    assertMarkerIsWholeAndAlone(tag(item), width, "projects ${item.issue.kind} $width x $height")
                    assertMarkerIsWholeAndAlone(syncIssueMarkerTag(SyncIssueTarget.PROJECT, "p2"), width, "projects second card $width x $height")
                }
            }
            everyKindOnStages.forEach { item ->
                onScreen(Screen.PROJECT, listOf(item, issueItem(SyncIssueTarget.STAGE, "st2", issueOf(SyncIssueKind.DELETE_REFUSED))), width, height, fontScale = 2f) { _, _, _ ->
                    awaitMarker(tag(item))
                    assertMarkerIsWholeAndAlone(tag(item), width, "project ${item.issue.kind} $width x $height")
                    assertMarkerIsWholeAndAlone(syncIssueMarkerTag(SyncIssueTarget.STAGE, "st2"), width, "project second stage $width x $height")
                }
            }
            onScreen(Screen.DAY, onDay, width, height, fontScale = 2f) { _, _, _ ->
                onDay.forEach { awaitMarker(tag(it)) }
                onDay.forEach { assertMarkerIsWholeAndAlone(tag(it), width, "day ${it.target} $width x $height") }
            }
        }
    }
}
