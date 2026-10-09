package com.dmb.chantiertracker.presentation

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.repository.RetryOutcome
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
class SyncIssuesScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val serverCodes = mapOf(
        RefusalReason.PLAN_LIMIT to "PLAN_LIMIT_EXCEEDED",
        RefusalReason.PROJECT_OR_STAGE_INACTIVE to "PROJECT_OR_STAGE_INACTIVE",
        RefusalReason.ENTRY_DATE_RESTRICTED to "ENTRY_DATE_RESTRICTED",
        RefusalReason.INSUFFICIENT_ROLE to "PROJECT_INSUFFICIENT_ROLE",
        RefusalReason.INSUFFICIENT_STOCK to "INSUFFICIENT_STOCK",
        RefusalReason.STOCK_CONSUMED to "STOCK_CONSUMED",
        RefusalReason.DUPLICATE_ENTRY to "DUPLICATE_ENTRY",
        RefusalReason.DUPLICATE_MATERIAL to "DUPLICATE_MATERIAL",
        RefusalReason.INVALID_VALUE to "VALIDATION_FAILED",
        RefusalReason.FILE_REFUSED to "ATTACHMENT_TOO_LARGE",
        RefusalReason.UNKNOWN to "SOME_CODE_THE_APP_DOES_NOT_KNOW",
    )

    private val frenchSentences = mapOf(
        RefusalReason.PLAN_LIMIT to "La limite de votre formule est atteinte.",
        RefusalReason.PROJECT_OR_STAGE_INACTIVE to "Le projet est suspendu ou terminé, ou l'étape est terminée.",
        RefusalReason.ENTRY_DATE_RESTRICTED to "Un superviseur ne peut saisir que sur la journée en cours.",
        RefusalReason.INSUFFICIENT_ROLE to "Cette action est réservée à un administrateur du projet.",
        RefusalReason.INSUFFICIENT_STOCK to "Le stock disponible est insuffisant.",
        RefusalReason.STOCK_CONSUMED to "Ce stock a déjà été consommé.",
        RefusalReason.DUPLICATE_ENTRY to "Une saisie de ce type existe déjà pour cette journée.",
        RefusalReason.DUPLICATE_MATERIAL to "Un matériau de ce nom existe déjà.",
        RefusalReason.INVALID_VALUE to "Une valeur saisie n'est pas acceptée.",
        RefusalReason.FILE_REFUSED to "Le fichier n'est pas accepté (durée, format ou taille).",
        RefusalReason.UNKNOWN to "Le serveur a refusé cet élément.",
    )

    private val englishSentences = mapOf(
        RefusalReason.PLAN_LIMIT to "Your plan's limit has been reached.",
        RefusalReason.PROJECT_OR_STAGE_INACTIVE to "The project is suspended or completed, or the stage is completed.",
        RefusalReason.ENTRY_DATE_RESTRICTED to "A supervisor can only enter data for the current day.",
        RefusalReason.INSUFFICIENT_ROLE to "This action is reserved for a project administrator.",
        RefusalReason.INSUFFICIENT_STOCK to "There is not enough stock available.",
        RefusalReason.STOCK_CONSUMED to "This stock has already been used.",
        RefusalReason.DUPLICATE_ENTRY to "An entry of this type already exists for this day.",
        RefusalReason.DUPLICATE_MATERIAL to "A material with this name already exists.",
        RefusalReason.INVALID_VALUE to "One of the values entered is not accepted.",
        RefusalReason.FILE_REFUSED to "The file is not accepted (duration, format or size).",
        RefusalReason.UNKNOWN to "The server refused this item.",
    )

    private fun onDay(target: SyncIssueTarget, id: String, issue: SyncIssue, label: String? = null, unit: String? = null, quantity: Double? = null, serverQuantity: Double? = null) =
        issueItem(
            target, id, issue, stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", date = "2026-10-09",
            entryType = EntryType.PURCHASE, label = label, unit = unit, quantity = quantity, serverQuantity = serverQuantity,
        )

    private val suspendedEntry = onDay(SyncIssueTarget.ENTRY, "e1", refusedIssue(RefusalReason.PROJECT_OR_STAGE_INACTIVE))
    private val waitingLine = onDay(SyncIssueTarget.PURCHASE_LINE, "pl1", SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT), label = "Ciment", unit = "sac", quantity = 3.0)

    private fun onScreen(
        items: List<SyncIssueItem>,
        locale: String = "fr",
        width: Int = 412,
        height: Int = 900,
        repo: FakeSyncIssueRepository = FakeSyncIssueRepository(items),
        block: ComposeUiTest.(FakeSyncIssueRepository) -> Unit,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val vm = SyncIssuesViewModel(repo)
        setContent {
            customAppLocale = locale
            AppEnvironment { AppTheme { SyncIssuesScreen(viewModel = vm) } }
        }
        waitForIdle()
        block(repo)
    }

    private fun ComposeUiTest.everythingShown(): List<String> {
        val texts = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { texts += it }
            node.children.forEach(::visit)
        }
        visit(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return texts
    }

    private fun oneItemPerReason() = RefusalReason.entries.mapIndexed { index, reason ->
        issueItem(SyncIssueTarget.MATERIAL, "m$index", SyncIssue(SyncIssueKind.REFUSED, reason, serverCodes.getValue(reason)), label = "Matériau $index")
    }

    @Test
    fun every_reason_reads_as_one_plain_sentence_in_french_and_in_english() {
        assertEquals(RefusalReason.entries.toSet(), frenchSentences.keys)
        assertEquals(RefusalReason.entries.toSet(), englishSentences.keys)
        onScreen(oneItemPerReason(), height = 4000) {
            frenchSentences.values.forEach { sentence -> onNodeWithText(sentence).assertExists() }
        }
        onScreen(oneItemPerReason(), locale = "en", height = 4000) {
            englishSentences.values.forEach { sentence -> onNodeWithText(sentence).assertExists() }
        }
    }

    @Test
    fun no_server_code_and_no_raw_server_text_is_ever_shown() {
        val looksLikeACode = Regex("[A-Z]{2,}_[A-Z_]{2,}")
        listOf("fr", "en").forEach { locale ->
            onScreen(oneItemPerReason(), locale = locale, height = 4000) {
                val shown = everythingShown()
                assertTrue(shown.size > RefusalReason.entries.size)
                serverCodes.values.forEach { code ->
                    assertTrue(shown.none { it.contains(code) }, "the server code $code must never reach the screen ($locale)")
                }
                assertTrue(shown.none { looksLikeACode.containsMatchIn(it) }, "nothing shaped like a technical code ($locale): ${shown.filter { looksLikeACode.containsMatchIn(it) }}")
            }
        }
    }

    @Test
    fun items_are_shown_under_their_project_their_stage_and_their_day() = onScreen(
        listOf(
            suspendedEntry,
            waitingLine,
            issueItem(SyncIssueTarget.PROJECT, "p0", refusedIssue(RefusalReason.PLAN_LIMIT), projectLocalId = "p0", projectName = "Atelier"),
        ),
    ) {
        onNodeWithText("3 éléments à revoir").assertExists()
        onNodeWithText("Atelier").assertExists()
        onNodeWithText("Villa Vidal").assertExists()
        onNodeWithText("Étape : Charpente").assertExists()
        onNodeWithText("Journée du 09-10-2026").assertExists()
        onNodeWithText("Saisie d'achats").assertExists()
        onNodeWithText("Ligne d'achat : Ciment, 3 sac").assertExists()
        onNodeWithText("Projet").assertExists()

        val atelier = onNodeWithText("Atelier").getUnclippedBoundsInRoot().top
        val villa = onNodeWithText("Villa Vidal").getUnclippedBoundsInRoot().top
        val stage = onNodeWithText("Étape : Charpente").getUnclippedBoundsInRoot().top
        val day = onNodeWithText("Journée du 09-10-2026").getUnclippedBoundsInRoot().top
        val entry = onNodeWithText("Saisie d'achats").getUnclippedBoundsInRoot().top
        val line = onNodeWithText("Ligne d'achat : Ciment, 3 sac").getUnclippedBoundsInRoot().top
        assertTrue(atelier < villa && villa < stage && stage < day && day < entry && entry < line, "project, stage, day, entry, then its line")
    }

    @Test
    fun a_refusal_that_depends_on_something_else_says_what_to_do_and_offers_retry() = onScreen(listOf(suspendedEntry, waitingLine)) { repo ->
        onNodeWithText("Refusé par le serveur").assertExists()
        onNodeWithText("Le projet est suspendu ou terminé, ou l'étape est terminée.").assertExists()
        onNodeWithText("Demandez à un administrateur du projet de rouvrir le projet ou l'étape, puis réessayez.").assertExists()
        onAllNodesWithText("Réessayer").assertCountEquals(1)

        onNodeWithText("Réessayer").performClick()
        waitUntil(timeoutMillis = 5_000L) { repo.retried.isNotEmpty() }

        assertEquals(listOf(suspendedEntry), repo.retried)
        waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText("Envoyé : le serveur a accepté.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun each_retryable_reason_has_its_own_instruction_in_both_languages() {
        val retryable = listOf(RefusalReason.PLAN_LIMIT, RefusalReason.PROJECT_OR_STAGE_INACTIVE, RefusalReason.ENTRY_DATE_RESTRICTED, RefusalReason.INSUFFICIENT_ROLE)
        val items = retryable.mapIndexed { index, reason -> issueItem(SyncIssueTarget.MATERIAL, "m$index", refusedIssue(reason), label = "M$index") }
        onScreen(items, height = 2400) {
            onNodeWithText("Changez de formule ou supprimez un projet, puis réessayez.").assertExists()
            onNodeWithText("Demandez à un administrateur du projet de rouvrir le projet ou l'étape, puis réessayez.").assertExists()
            onNodeWithText("Demandez à un administrateur du projet de faire cette saisie, ou de vous donner ses droits, puis réessayez.").assertExists()
            onNodeWithText("Demandez à un administrateur du projet de le faire, ou de vous donner ses droits, puis réessayez.").assertExists()
            onAllNodesWithText("Réessayer").assertCountEquals(4)
        }
        onScreen(items, locale = "en", height = 2400) {
            onNodeWithText("Change your plan or delete a project, then retry.").assertExists()
            onNodeWithText("Ask a project administrator to reopen the project or the stage, then retry.").assertExists()
            onNodeWithText("Ask a project administrator to make this entry, or to give you administrator rights, then retry.").assertExists()
            onNodeWithText("Ask a project administrator to do it, or to give you administrator rights, then retry.").assertExists()
            onAllNodesWithText("Retry").assertCountEquals(4)
        }
    }

    @Test
    fun a_refusal_only_a_correction_can_lift_offers_no_retry() = onScreen(
        listOf(
            onDay(SyncIssueTarget.CONSUMPTION_LINE, "cl1", refusedIssue(RefusalReason.INSUFFICIENT_STOCK), label = "Ciment", unit = "sac", quantity = 50.0),
            issueItem(SyncIssueTarget.ATTACHMENT, "a1", refusedIssue(RefusalReason.FILE_REFUSED), stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", date = "2026-10-09", label = "facture.jpg"),
        ),
    ) {
        onNodeWithText("Consommation : Ciment, 50 sac").assertExists()
        onNodeWithText("Le stock disponible est insuffisant.").assertExists()
        onNodeWithText("Justificatif : facture.jpg").assertExists()
        onNodeWithText("Le fichier n'est pas accepté (durée, format ou taille).").assertExists()
        onAllNodesWithText("Réessayer").assertCountEquals(0)
    }

    @Test
    fun a_child_waiting_on_a_refused_parent_says_so_and_has_no_action_of_its_own() = onScreen(listOf(waitingLine)) {
        onNodeWithText("En attente").assertExists()
        onNodeWithText("Sera envoyé dès que l'élément refusé dont il dépend sera accepté.").assertExists()
        onAllNodesWithText("Réessayer").assertCountEquals(0)
    }

    @Test
    fun the_other_states_each_have_their_label_and_their_sentence() = onScreen(
        listOf(
            onDay(SyncIssueTarget.PURCHASE_LINE, "pl-edit", refusedIssue(RefusalReason.STOCK_CONSUMED, kind = SyncIssueKind.UPDATE_REFUSED), label = "Ciment", unit = "sac", quantity = 3.0, serverQuantity = 10.0),
            issueItem(SyncIssueTarget.STAGE, "st1", refusedIssue(RefusalReason.INSUFFICIENT_ROLE, kind = SyncIssueKind.DELETE_REFUSED), stageLocalId = "st1", stageName = "Charpente"),
            onDay(SyncIssueTarget.ENTRY, "e-gone", SyncIssue(SyncIssueKind.DELETED_ON_SERVER)).copy(entryType = EntryType.WORK),
        ),
        height = 1600,
    ) {
        onNodeWithText("Modification refusée").assertExists()
        onNodeWithText("Ce stock a déjà été consommé.").assertExists()
        onNodeWithText("Vous avez saisi 3 ; le serveur a gardé 10.").assertExists()
        onNodeWithText("Suppression refusée").assertExists()
        onNodeWithText("La suppression a été refusée : l'élément a été rétabli. Cette action est réservée à un administrateur du projet.").assertExists()
        onNodeWithText("Supprimé sur le serveur").assertExists()
        onNodeWithText("Saisie de travaux").assertExists()
        onNodeWithText("Cet élément a été supprimé sur le serveur, ou ne vous est plus accessible.").assertExists()
        onAllNodesWithText("Réessayer").assertCountEquals(0)
    }

    @Test
    fun while_a_retry_runs_the_button_is_disabled_and_says_it_is_sending() {
        val repo = FakeSyncIssueRepository(listOf(suspendedEntry))
        repo.retryGate = CompletableDeferred()
        onScreen(emptyList(), repo = repo) {
            onNodeWithText("Réessayer").performClick()
            waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText("Envoi en cours…").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Envoi en cours…").assertIsNotEnabled()
            repo.retryGate!!.complete(Unit)
            waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText("Envoi en cours…").fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun a_retry_still_refused_or_not_sent_tells_the_user_in_a_live_region() {
        val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        val stillRefused = FakeSyncIssueRepository(listOf(suspendedEntry)).apply { retryOutcome = RetryOutcome.STILL_REFUSED }
        onScreen(emptyList(), repo = stillRefused) {
            onNodeWithText("Réessayer").performClick()
            waitUntil(timeoutMillis = 5_000L) { onAllNodesWithText("Toujours refusé par le serveur.").fetchSemanticsNodes().isNotEmpty() }
            onNode(liveRegion.and(hasText("Toujours refusé par le serveur."))).assertExists()
            onNodeWithContentDescription("Fermer").performClick()
            waitForIdle()
            onAllNodesWithText("Toujours refusé par le serveur.").assertCountEquals(0)
        }
        val notSent = FakeSyncIssueRepository(listOf(suspendedEntry)).apply { retryOutcome = RetryOutcome.NOT_SENT }
        onScreen(emptyList(), locale = "en", repo = notSent) {
            onNodeWithText("Retry").performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithText("Could not send right now. Check your connection, then retry.").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun nothing_to_review_shows_an_empty_state_in_both_languages() {
        onScreen(emptyList()) {
            onNodeWithText("Rien à revoir").assertExists()
            onNodeWithText("Le serveur n'a refusé aucune de vos saisies.").assertExists()
        }
        onScreen(emptyList(), locale = "en") {
            onNodeWithText("Nothing to review").assertExists()
            onNodeWithText("The server has not refused any of your entries.").assertExists()
        }
    }

    @Test
    fun the_screen_is_translated() = onScreen(listOf(suspendedEntry, waitingLine), locale = "en") {
        onNodeWithText("2 items to review").assertExists()
        onNodeWithText("Stage: Charpente").assertExists()
        onNodeWithText("Day of 09-10-2026").assertExists()
        onNodeWithText("Purchases entry").assertExists()
        onNodeWithText("Refused by the server").assertExists()
        onNodeWithText("Purchase line: Ciment, 3 sac").assertExists()
        onNodeWithText("Waiting").assertExists()
        onNodeWithText("Will be sent as soon as the refused item it depends on is accepted.").assertExists()
    }

    @Test
    fun group_titles_are_headings_for_a_screen_reader() = onScreen(listOf(suspendedEntry, waitingLine)) {
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        onNode(heading.and(hasText("Villa Vidal"))).assertExists()
        onNode(heading.and(hasText("Étape : Charpente"))).assertExists()
        onNode(heading.and(hasText("Journée du 09-10-2026"))).assertExists()
    }

    private fun ComposeUiTest.assertEverythingFitsIn(width: Int) {
        listOf(
            "Villa Vidal", "Étape : Charpente", "Journée du 09-10-2026", "Saisie d'achats", "Refusé par le serveur",
            "Le projet est suspendu ou terminé, ou l'étape est terminée.",
            "Demandez à un administrateur du projet de rouvrir le projet ou l'étape, puis réessayez.", "Réessayer",
        ).forEach { text ->
            val bounds = onNodeWithText(text).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= 0.dp && bounds.right <= width.dp, "« $text » fits in $width dp: $bounds")
        }
        onNodeWithText("Réessayer").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun on_a_small_phone_nothing_is_cut_off_and_retry_stays_a_full_size_target() =
        onScreen(listOf(suspendedEntry, waitingLine), width = 320, height = 640) { assertEverythingFitsIn(320) }

    @Test
    fun on_a_tablet_the_content_is_capped_and_centered() = onScreen(listOf(suspendedEntry, waitingLine), width = 800, height = 1280) {
        assertEverythingFitsIn(800)
        val card = onNodeWithText("Le projet est suspendu ou terminé, ou l'étape est terminée.").getUnclippedBoundsInRoot()
        assertTrue(card.left >= 80.dp && card.right <= 720.dp, "content band of 640 dp centered in 800 dp: $card")
    }

    @Test
    fun on_a_desktop_window_the_content_is_capped_and_centered() = onScreen(listOf(suspendedEntry, waitingLine), width = 1440, height = 900) {
        assertEverythingFitsIn(1440)
        val card = onNodeWithText("Le projet est suspendu ou terminé, ou l'étape est terminée.").getUnclippedBoundsInRoot()
        assertTrue(card.left >= 400.dp && card.right <= 1040.dp, "content band of 640 dp centered in 1440 dp: $card")
    }
}
