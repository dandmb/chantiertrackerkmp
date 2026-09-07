package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.HistoryActionType
import com.dmb.chantiertracker.domain.model.HistoryPage
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ModificationHistoryItem
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryScreen
import com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeHistoryRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProjectHistoryUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun item(id: Long, description: String?) = ModificationHistoryItem(
        id = id, modifiedAt = "2026-09-0${id}T14:32:00", actionType = HistoryActionType.MODIFICATION,
        description = description, entryId = null, userId = 1L, fieldName = null, oldValue = null, newValue = null,
    )

    private fun page(index: Int, total: Int, items: List<ModificationHistoryItem>) = HistoryPage(
        items = items, page = index, totalPages = total,
        isFirst = index == 0, isLast = index == total - 1, totalElements = total * 20,
    )

    private fun vm(history: FakeHistoryRepository, ownerPlan: Plan? = Plan.LIBERTE) = ProjectHistoryViewModel(
        history,
        FakeProjectRepository(
            detail = ProjectDetail(
                localId = "p1", name = "Villa", description = null, location = null,
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = 1L, ownerPlan = ownerPlan,
            ),
        ),
    )

    private fun ComposeUiTest.content(history: FakeHistoryRepository, ownerPlan: Plan? = Plan.LIBERTE) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        ProjectHistoryScreen("p1", viewModel = vm(history, ownerPlan))
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun lists_entries_and_a_liberte_project_shows_no_retention_notice() = runComposeUiTest {
        content(FakeHistoryRepository(listOf(page(0, 1, listOf(
            item(1, "Dan a créé le projet Villa"),
            item(2, "Dan a modifié le budget : 500 000 → 600 000 EUR"),
        )))))

        awaitText("Dan a créé le projet Villa")
        onNodeWithText("Dan a modifié le budget : 500 000 → 600 000 EUR").assertIsDisplayed()
        onNodeWithText("01-09-2026 · 14:32", substring = true).assertIsDisplayed()
        onNodeWithText("Historique limité", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_free_project_shows_the_30_day_retention_notice() = runComposeUiTest {
        content(FakeHistoryRepository(listOf(page(0, 1, listOf(item(1, "Dan a créé le projet"))))), ownerPlan = Plan.FREE)

        awaitText("Historique limité aux 30 derniers jours")
    }

    @Test
    fun choosing_a_different_sort_reloads_from_page_zero() = runComposeUiTest {
        val history = FakeHistoryRepository(listOf(page(0, 1, listOf(item(1, "Dan a créé le projet")))))
        content(history)
        awaitText("Dan a créé le projet")

        onNodeWithText("Plus récent").performClick()
        awaitText("Par type d'action")
        onNodeWithText("Par type d'action (A→Z)").performClick()
        waitUntil(timeoutMillis = 5_000L) { history.calls.lastOrNull()?.third == HistorySort.BY_ACTION }

        assert(history.calls.last() == Triple("p1", 0, HistorySort.BY_ACTION))
    }

    @Test
    fun pagination_appears_with_several_pages_and_next_advances() = runComposeUiTest {
        content(FakeHistoryRepository(listOf(
            page(0, 2, listOf(item(1, "Page 1 entry"))),
            page(1, 2, listOf(item(2, "Page 2 entry"))),
        )))

        awaitText("Page 1 / 2")
        onNodeWithText("Précédent").assertIsNotEnabled()

        onNodeWithText("Suivant").performClick()
        awaitText("Page 2 / 2")
        onNodeWithText("Page 2 entry").assertIsDisplayed()
    }

    @Test
    fun the_empty_state_is_shown() = runComposeUiTest {
        content(FakeHistoryRepository(listOf(page(0, 1, emptyList()))))

        awaitText("Aucun historique pour l'instant.")
    }

    @Test
    fun an_error_shows_a_retry_button_that_refetches() = runComposeUiTest {
        val history = FakeHistoryRepository(listOf(page(0, 1, listOf(item(1, "Recovered entry")))))
            .apply { error = DomainException.Network }
        content(history)

        awaitText("Réessayer")
        history.error = null
        onNodeWithText("Réessayer").performClick()

        awaitText("Recovered entry")
    }
}
