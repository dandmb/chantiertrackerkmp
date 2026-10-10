package com.dmb.chantiertracker.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueParent
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.repository.RevertOutcome
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.sync.SyncIssuesScreen
import com.dmb.chantiertracker.presentation.sync.SyncIssuesViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeSyncIssueRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.issueItem
import com.dmb.chantiertracker.support.refusedIssue
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SyncIssueActionsUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val french = listOf("Corriger", "Réessayer", "Annuler ma modification", "Abandonner", "J'ai compris")
    private val english = listOf("Fix", "Retry", "Undo my change", "Discard", "Got it")

    private fun onDay(target: SyncIssueTarget, id: String, issue: SyncIssue, type: EntryType = EntryType.PURCHASE, label: String? = null, quantity: Double? = null, serverQuantity: Double? = null) =
        issueItem(
            target, id, issue, stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", date = "2026-10-09", entryType = type,
            label = label, unit = label?.let { "sac" }, quantity = quantity, serverQuantity = serverQuantity, entryLocalId = "e1", currency = "EUR",
        )

    private val duplicateEntry = onDay(SyncIssueTarget.ENTRY, "e-dup", refusedIssue(RefusalReason.DUPLICATE_ENTRY))
    private val refusedFile = onDay(SyncIssueTarget.ATTACHMENT, "a1", refusedIssue(RefusalReason.FILE_REFUSED), label = "facture.jpg").copy(unit = null)
    private val tooMuch = onDay(SyncIssueTarget.CONSUMPTION_LINE, "cl1", refusedIssue(RefusalReason.INSUFFICIENT_STOCK), type = EntryType.WORK, label = "Ciment", quantity = 50.0)
    private val suspended = onDay(SyncIssueTarget.ENTRY, "e-susp", refusedIssue(RefusalReason.PROJECT_OR_STAGE_INACTIVE))
    private val unknownStage = issueItem(SyncIssueTarget.STAGE, "st-unknown", refusedIssue(RefusalReason.UNKNOWN, serverCode = "A_CODE_FROM_A_NEWER_SERVER"), stageLocalId = "st-unknown", stageName = "Bardage")
    private val unknownMaterial = issueItem(SyncIssueTarget.MATERIAL, "m-unknown", refusedIssue(RefusalReason.UNKNOWN, serverCode = null), label = "Gravier")
    private val goneEntry = onDay(SyncIssueTarget.ENTRY, "e-gone", SyncIssue(SyncIssueKind.DELETED_ON_SERVER), type = EntryType.WORK)
    private val refusedDelete = issueItem(SyncIssueTarget.STAGE, "st-del", refusedIssue(RefusalReason.INSUFFICIENT_ROLE, kind = SyncIssueKind.DELETE_REFUSED), stageLocalId = "st-del", stageName = "Toiture")
    private val refusedChange = onDay(SyncIssueTarget.PURCHASE_LINE, "pl-edit", refusedIssue(RefusalReason.STOCK_CONSUMED, kind = SyncIssueKind.UPDATE_REFUSED), label = "Ciment", quantity = 3.0, serverQuantity = 10.0)
    private val refusedChangeKnownHere =
        onDay(SyncIssueTarget.CONSUMPTION_LINE, "cl-edit", refusedIssue(RefusalReason.INSUFFICIENT_STOCK, kind = SyncIssueKind.UPDATE_REFUSED), type = EntryType.WORK, label = "Sable", quantity = 50.0, serverQuantity = 2.0)
    private val refusedChangeOnSuspended = onDay(SyncIssueTarget.ENTRY, "e-edit", refusedIssue(RefusalReason.PROJECT_OR_STAGE_INACTIVE, kind = SyncIssueKind.UPDATE_REFUSED))
    private val waitingLine = onDay(SyncIssueTarget.PURCHASE_LINE, "pl-wait", SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT), label = "Ciment", quantity = 3.0)
        .copy(blockedBy = SyncIssueParent(SyncIssueTarget.ENTRY, entryType = EntryType.PURCHASE, date = "2026-10-09"))

    private class Screen(val repo: FakeSyncIssueRepository, val fixed: MutableList<SyncIssueItem>)

    private fun onScreen(
        items: List<SyncIssueItem>,
        locale: String = "fr",
        width: Int = 412,
        height: Int = 1200,
        fontScale: Float = 1f,
        repo: FakeSyncIssueRepository = FakeSyncIssueRepository(items),
        block: ComposeUiTest.(Screen) -> Unit,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val vm = SyncIssuesViewModel(repo)
        val fixed = mutableListOf<SyncIssueItem>()
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        Surface { SyncIssuesScreen(viewModel = vm, onFix = { fixed += it }) }
                    }
                }
            }
        }
        waitForIdle()
        block(Screen(repo, fixed))
    }

    private fun ComposeUiTest.buttonsShown(labels: List<String>): List<String> =
        labels.filter { onAllNodes(hasText(it).and(hasClickAction())).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.everythingShown(): List<String> {
        val texts = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { texts += it }
            node.children.forEach(::visit)
        }
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        return texts
    }

    private fun ComposeUiTest.await(text: String) = waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.awaitGone(text: String) = waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    @Test
    fun each_kind_of_refused_entry_shows_only_the_actions_that_make_sense_for_it() {
        val expected = listOf(
            duplicateEntry to listOf("Abandonner"),
            refusedFile to listOf("Abandonner"),
            tooMuch to listOf("Corriger", "Abandonner"),
            suspended to listOf("Réessayer", "Abandonner"),
            unknownStage to listOf("Corriger", "J'ai compris"),
            unknownMaterial to listOf("J'ai compris"),
            goneEntry to listOf("J'ai compris"),
            refusedDelete to listOf("J'ai compris"),
            refusedChange to listOf("Corriger", "Annuler ma modification"),
            refusedChangeOnSuspended to listOf("Réessayer", "Annuler ma modification"),
            waitingLine to emptyList(),
        )
        expected.forEach { (item, actions) ->
            onScreen(listOf(item)) { assertEquals(actions, buttonsShown(french), "${item.target} ${item.issue.kind} ${item.issue.reason}") }
        }
    }

    @Test
    fun the_actions_are_translated() {
        onScreen(listOf(tooMuch)) { assertEquals(listOf("Corriger", "Abandonner"), buttonsShown(french)) }
        onScreen(listOf(tooMuch), locale = "en") { assertEquals(listOf("Fix", "Discard"), buttonsShown(english)) }
        onScreen(listOf(refusedChangeOnSuspended), locale = "en") { assertEquals(listOf("Retry", "Undo my change"), buttonsShown(english)) }
        onScreen(listOf(goneEntry), locale = "en") { assertEquals(listOf("Got it"), buttonsShown(english)) }
    }

    @Test
    fun fixing_hands_the_refused_entry_to_its_form() = onScreen(listOf(tooMuch)) { screen ->
        onNodeWithText("Corriger").assertHeightIsAtLeast(48.dp).performClick()
        waitForIdle()

        assertEquals(listOf(tooMuch), screen.fixed)
        assertTrue(screen.repo.actions.isEmpty(), "fixing changes nothing by itself: the form does, on save")
    }

    @Test
    fun discarding_a_parent_announces_how_many_linked_entries_go_with_it_before_anything_is_removed() {
        val repo = FakeSyncIssueRepository(listOf(duplicateEntry, waitingLine)).apply { linked = 3 }
        onScreen(emptyList(), repo = repo) { screen ->
            onNodeWithText("Abandonner").performClick()
            await("Abandonner cet élément ?")

            onNodeWithText("Il n'a jamais été enregistré sur le serveur. Il sera supprimé de cet appareil.").assertExists()
            onNodeWithText("3 saisies liées seront aussi supprimées.").assertExists()
            assertTrue(screen.repo.actions.isEmpty())

            onNode(hasText("Abandonner").and(hasClickAction()).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).performClick()
            waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }
            assertEquals(listOf("discard ${duplicateEntry.key}"), screen.repo.actions)
            await("Élément abandonné : il est supprimé de cet appareil.")
            onAllNodesWithText("Abandonner cet élément ?").assertCountEquals(0)
        }
    }

    @Test
    fun the_number_of_linked_entries_is_worded_in_the_singular_and_left_out_at_zero() {
        onScreen(emptyList(), repo = FakeSyncIssueRepository(listOf(duplicateEntry)).apply { linked = 1 }) {
            onNodeWithText("Abandonner").performClick()
            await("Abandonner cet élément ?")
            onNodeWithText("1 saisie liée sera aussi supprimée.").assertExists()
        }
        onScreen(listOf(duplicateEntry)) {
            onNodeWithText("Abandonner").performClick()
            await("Abandonner cet élément ?")
            onAllNodesWithText("supprimée", substring = true).assertCountEquals(0)
            onAllNodesWithText("supprimées", substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun cancelling_the_confirmation_keeps_the_entry() = onScreen(listOf(duplicateEntry)) { screen ->
        onNodeWithText("Abandonner").performClick()
        await("Abandonner cet élément ?")

        onNodeWithText("Annuler").performClick()
        awaitGone("Abandonner cet élément ?")

        assertTrue(screen.repo.actions.isEmpty())
        onNodeWithText("Saisie d'achats").assertExists()
    }

    @Test
    fun discarding_a_refused_file_says_the_local_file_goes_too() = onScreen(listOf(refusedFile)) { screen ->
        onNodeWithText("Abandonner").performClick()
        await("Abandonner cet élément ?")

        onNodeWithText("Le fichier sera aussi supprimé de cet appareil.").assertExists()
        onNode(hasText("Abandonner").and(hasClickAction()).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).performClick()
        waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }
        assertEquals(listOf("discard ${refusedFile.key}"), screen.repo.actions)
    }

    @Test
    fun the_confirmation_is_translated() = onScreen(emptyList(), locale = "en", repo = FakeSyncIssueRepository(listOf(refusedFile)).apply { linked = 2 }) {
        onNodeWithText("Discard").performClick()
        await("Discard this item?")
        onNodeWithText("It was never saved on the server. It will be deleted from this device.").assertExists()
        onNodeWithText("2 linked entries will be deleted too.").assertExists()
        onNodeWithText("The file will be deleted from this device too.").assertExists()
    }

    @Test
    fun got_it_removes_a_leaf_at_once_and_says_so() = onScreen(listOf(goneEntry, duplicateEntry)) { screen ->
        onNodeWithText("J'ai compris").performClick()
        waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }

        assertEquals(listOf("acknowledge ${goneEntry.key}"), screen.repo.actions)
        await("Élément retiré de la liste.")
        onAllNodesWithText("Retirer cet élément de la liste ?").assertCountEquals(0)
        await("1 élément à revoir")
    }

    @Test
    fun got_it_on_a_parent_with_linked_entries_asks_first() {
        val repo = FakeSyncIssueRepository(listOf(unknownStage)).apply { linked = 2 }
        onScreen(emptyList(), repo = repo) { screen ->
            onNodeWithText("J'ai compris").performClick()
            await("Retirer cet élément de la liste ?")
            onNodeWithText("Il sera supprimé de cet appareil.").assertExists()
            onNodeWithText("2 saisies liées seront aussi supprimées.").assertExists()
            assertTrue(screen.repo.actions.isEmpty())

            onNode(hasText("J'ai compris").and(hasClickAction()).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).performClick()
            waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }
            assertEquals(listOf("acknowledge ${unknownStage.key}"), screen.repo.actions)
            await("Rien à revoir")
        }
    }

    @Test
    fun undoing_a_change_online_reports_the_server_value_is_back() = onScreen(listOf(refusedChange)) { screen ->
        onNodeWithText("Annuler ma modification").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }

        assertEquals(listOf("revert ${refusedChange.key}"), screen.repo.actions)
        await("Modification annulée : la valeur du serveur est rétablie.")
    }

    @Test
    fun offline_undo_is_disabled_with_a_sentence_that_explains_why_unless_the_server_value_is_on_the_device() {
        val explanation = "Hors ligne : la valeur du serveur n'est pas connue sur cet appareil. Reconnectez-vous pour annuler votre modification."
        val repo = FakeSyncIssueRepository(listOf(refusedChange)).apply { online.value = false }
        onScreen(emptyList(), repo = repo) { screen ->
            onNodeWithText("Annuler ma modification").assertIsNotEnabled()
            onNodeWithText(explanation).assertIsDisplayed()

            screen.repo.online.value = true
            waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText(explanation).fetchSemanticsNodes().isEmpty() }
            onNodeWithText("Annuler ma modification").assertIsEnabled()
        }
        val known = FakeSyncIssueRepository(listOf(refusedChangeKnownHere)).apply { online.value = false }
        onScreen(emptyList(), repo = known) { screen ->
            onAllNodesWithText(explanation).assertCountEquals(0)
            onNodeWithText("Annuler ma modification").assertIsEnabled().performClick()
            waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }
            assertEquals(listOf("revert ${refusedChangeKnownHere.key}"), screen.repo.actions)
        }
        val english = FakeSyncIssueRepository(listOf(refusedChange)).apply { online.value = false }
        onScreen(emptyList(), locale = "en", repo = english) {
            onNodeWithText("Offline: the server value is not known on this device. Reconnect to undo your change.").assertIsDisplayed()
        }
    }

    @Test
    fun an_undo_that_fails_says_so_and_keeps_the_entry_listed() {
        val failing = FakeSyncIssueRepository(listOf(refusedChange)).apply { revertOutcome = RevertOutcome.FAILED }
        onScreen(emptyList(), repo = failing) {
            onNodeWithText("Annuler ma modification").performClick()
            await("Annulation impossible pour le moment. Réessayez plus tard.")
            onNodeWithText("Modification refusée").assertExists()
        }
        val gone = FakeSyncIssueRepository(listOf(refusedChange)).apply { revertOutcome = RevertOutcome.GONE_ON_SERVER }
        onScreen(emptyList(), repo = gone) {
            onNodeWithText("Annuler ma modification").performClick()
            await("Cet élément a été supprimé sur le serveur. Votre modification ne peut plus être annulée.")
        }
        val goneInEnglish = FakeSyncIssueRepository(listOf(refusedChange)).apply { revertOutcome = RevertOutcome.GONE_ON_SERVER }
        onScreen(emptyList(), locale = "en", repo = goneInEnglish) {
            onNodeWithText("Undo my change").performClick()
            await("This item was deleted on the server. Your change can no longer be undone.")
        }
        val lostConnection = FakeSyncIssueRepository(listOf(refusedChange)).apply { revertOutcome = RevertOutcome.NEEDS_CONNECTION }
        onScreen(emptyList(), repo = lostConnection) {
            onNodeWithText("Annuler ma modification").performClick()
            await("Annulation impossible hors ligne. Reconnectez-vous, puis réessayez.")
        }
    }

    @Test
    fun while_an_action_runs_every_button_is_disabled_so_a_double_tap_does_nothing() {
        val repo = FakeSyncIssueRepository(listOf(goneEntry, tooMuch, refusedChange))
        repo.actionGate = CompletableDeferred()
        onScreen(emptyList(), repo = repo, height = 2000) { screen ->
            onNodeWithText("J'ai compris").performClick()
            waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }
            waitForIdle()

            onAllNodesWithText("Corriger").assertCountEquals(2)
            onAllNodesWithText("Corriger").fetchSemanticsNodes().forEach { assertTrue(it.config.contains(SemanticsProperties.Disabled)) }
            onNodeWithText("Abandonner").assertIsNotEnabled()
            onNodeWithText("Annuler ma modification").assertIsNotEnabled()
            onAllNodes(hasText("En cours…")).onFirst().assertIsNotEnabled()

            screen.repo.actionGate!!.complete(Unit)
            await("Élément retiré de la liste.")
            assertEquals(1, screen.repo.actions.size)
            onAllNodesWithText("Corriger").fetchSemanticsNodes().forEach { assertTrue(!it.config.contains(SemanticsProperties.Disabled)) }
        }
    }

    @Test
    fun no_server_code_reaches_the_cards_the_dialogs_or_the_notices() {
        val looksLikeACode = Regex("[A-Z]{2,}_[A-Z_]{2,}")
        listOf("fr", "en").forEach { locale ->
            val repo = FakeSyncIssueRepository(listOf(unknownStage, refusedChange, goneEntry)).apply { linked = 2 }
            onScreen(emptyList(), locale = locale, repo = repo, height = 2000) {
                onAllNodes(hasText(if (locale == "fr") "J'ai compris" else "Got it").and(hasClickAction())).onFirst().performClick()
                waitUntil(timeoutMillis = 5_000L) { onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
                val shown = everythingShown()
                assertTrue(shown.size > 10)
                assertTrue(shown.none { it.contains("A_CODE_FROM_A_NEWER_SERVER") || looksLikeACode.containsMatchIn(it) }, "$locale: ${shown.filter { looksLikeACode.containsMatchIn(it) }}")
            }
        }
    }

    private fun ComposeUiTest.assertDialogIsUsableOn(width: Int, height: Int, texts: List<String>, confirm: String) {
        texts.forEachIndexed { index, text ->
            if (index > 0) onNode(hasText(text).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).performScrollTo()
            waitForIdle()
            val node = onNode(hasText(text).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).assertIsDisplayed().fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            assertTrue(layouts.isNotEmpty() && layouts.none { it.isCut() }, "« $text » is not cut in the dialog ($width x $height)")
        }
        listOf("Annuler", confirm).forEach { label ->
            val bounds = onNode(hasText(label).and(hasClickAction()).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(
                bounds.left >= 0.dp && bounds.right <= width.dp && bounds.top >= 0.dp && bounds.bottom <= height.dp,
                "« $label » is entirely on a $width x $height screen: $bounds",
            )
        }
    }

    private val discardTexts = listOf(
        "Abandonner cet élément ?", "Il n'a jamais été enregistré sur le serveur. Il sera supprimé de cet appareil.",
        "12 saisies liées seront aussi supprimées.", "Le fichier sera aussi supprimé de cet appareil.",
    )

    @Test
    fun at_200_percent_font_the_discard_confirmation_stays_usable_on_a_320_dp_phone_and_on_an_iphone_se() {
        listOf(320 to 640, 320 to 568, 375 to 667).forEach { (width, height) ->
            val repo = FakeSyncIssueRepository(listOf(refusedFile)).apply { linked = 12 }
            onScreen(emptyList(), width = width, height = height, fontScale = 2f, repo = repo) { screen ->
                onNode(hasScrollAction()).performScrollToNode(hasText("Abandonner"))
                onNodeWithText("Abandonner").performClick()
                await("Abandonner cet élément ?")

                assertDialogIsUsableOn(width, height, discardTexts, confirm = "Abandonner")

                onNode(hasText("Abandonner").and(hasClickAction()).and(androidx.compose.ui.test.hasAnyAncestor(isDialog()))).performClick()
                waitUntil(timeoutMillis = 5_000L) { screen.repo.actions.isNotEmpty() }
            }
        }
    }

    @Test
    fun at_200_percent_font_the_got_it_confirmation_stays_usable_on_an_iphone_se() {
        val repo = FakeSyncIssueRepository(listOf(unknownStage)).apply { linked = 2 }
        onScreen(emptyList(), width = 320, height = 568, fontScale = 2f, repo = repo) { screen ->
            onNode(hasScrollAction()).performScrollToNode(hasText("J'ai compris"))
            onNodeWithText("J'ai compris").performClick()
            await("Retirer cet élément de la liste ?")

            assertDialogIsUsableOn(320, 568, listOf("Retirer cet élément de la liste ?", "Il sera supprimé de cet appareil.", "2 saisies liées seront aussi supprimées."), confirm = "J'ai compris")

            onNodeWithText("Annuler").performClick()
            awaitGone("Retirer cet élément de la liste ?")
            assertTrue(screen.repo.actions.isEmpty())
        }
    }

    @Test
    fun at_200_percent_font_on_an_iphone_se_every_action_button_can_be_reached_is_full_size_and_is_never_cut() {
        val items = listOf(tooMuch, refusedChange, suspended, goneEntry)
        onScreen(items, width = 320, height = 568, fontScale = 2f, repo = FakeSyncIssueRepository(items).apply { online.value = false }) {
            val list = onNode(hasScrollAction())
            listOf("Corriger", "Abandonner", "Annuler ma modification", "Réessayer", "J'ai compris").forEach { label ->
                list.performScrollToNode(hasText(label).and(hasClickAction()))
                waitForIdle()
                val node = onAllNodes(hasText(label).and(hasClickAction())).onFirst().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= 0.dp && bounds.right <= 320.dp, "« $label » fits in 320 dp: $bounds")
            }
            val explanation = "Hors ligne : la valeur du serveur n'est pas connue sur cet appareil. Reconnectez-vous pour annuler votre modification."
            list.performScrollToNode(hasText(explanation))
            waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(explanation).assertIsDisplayed().fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            assertTrue(layouts.isNotEmpty() && layouts.none { it.isCut() })
        }
    }
}
