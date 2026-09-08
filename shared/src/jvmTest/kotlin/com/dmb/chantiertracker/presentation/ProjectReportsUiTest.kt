package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Report
import com.dmb.chantiertracker.domain.model.ReportPage
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.domain.model.ReportStatus
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.reports.ProjectReportsScreen
import com.dmb.chantiertracker.presentation.reports.ProjectReportsViewModel
import com.dmb.chantiertracker.presentation.reports.ReportSortControl
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeReportRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProjectReportsUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun report(id: Long, status: ReportStatus, message: String) = Report(
        id = id, entryId = id * 10, entryType = EntryType.PURCHASE, entryDate = "2026-09-0$id",
        authorName = "Sam Superviseur", message = message, createdAt = "2026-09-0${id}T09:00:00",
        status = status, processedAt = if (status == ReportStatus.PROCESSED) "2026-09-06T18:00:00" else null,
    )

    private fun page(index: Int, total: Int, items: List<Report>) = ReportPage(
        items = items, page = index, totalPages = total,
        isFirst = index == 0, isLast = index == total - 1, totalElements = total * 20,
    )

    private fun ComposeUiTest.content(repo: FakeReportRepository, sort: ReportSort = ReportSort.NEWEST_FIRST) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        ProjectReportsScreen("p1", sort = sort, viewModel = ProjectReportsViewModel(repo))
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
    fun new_reports_offer_an_action_processed_ones_show_a_badge() = runComposeUiTest {
        content(
            FakeReportRepository(
                listOf(page(0, 1, listOf(
                    report(1, ReportStatus.NEW, "Quantité de ciment suspecte"),
                    report(2, ReportStatus.PROCESSED, "Oubli déjà corrigé"),
                ))),
            ),
        )

        awaitText("Quantité de ciment suspecte")
        onNodeWithText("Achats du 01-09-2026 · signalé par Sam Superviseur le 01-09-2026", substring = true).assertIsDisplayed()
        onNodeWithText("Marquer comme traité").assertIsDisplayed()
        onNodeWithText("Traité").assertIsDisplayed()
        onNodeWithText("Traité le 06-09-2026").assertIsDisplayed()
    }

    @Test
    fun marking_a_report_processed_updates_the_row() = runComposeUiTest {
        content(FakeReportRepository(listOf(page(0, 1, listOf(report(1, ReportStatus.NEW, "À traiter"))))))

        awaitText("À traiter")
        onNodeWithText("Marquer comme traité").performClick()

        awaitText("Traité le")
        onNodeWithText("Marquer comme traité").assertDoesNotExist()
    }

    @Test
    fun a_process_failure_shows_a_banner_and_keeps_the_button() = runComposeUiTest {
        content(
            FakeReportRepository(listOf(page(0, 1, listOf(report(1, ReportStatus.NEW, "À traiter")))))
                .apply { processError = DomainException.Network },
        )

        awaitText("À traiter")
        onNodeWithText("Marquer comme traité").performClick()

        awaitText("Connexion au serveur impossible")
        onNodeWithText("Marquer comme traité").assertIsDisplayed()
    }

    @Test
    fun the_empty_state_is_shown() = runComposeUiTest {
        content(FakeReportRepository(listOf(page(0, 1, emptyList()))))

        awaitText("Aucun signalement.")
    }

    @Test
    fun pagination_appears_and_next_advances() = runComposeUiTest {
        content(
            FakeReportRepository(listOf(
                page(0, 2, listOf(report(1, ReportStatus.NEW, "Page 1"))),
                page(1, 2, listOf(report(2, ReportStatus.NEW, "Page 2"))),
            )),
        )

        awaitText("Page 1 / 2")
        onNodeWithText("Précédent").assertIsNotEnabled()

        onNodeWithText("Suivant").performClick()
        awaitText("Page 2 / 2")
        onNodeWithText("Page 2").assertIsDisplayed()
    }

    @Test
    fun an_error_shows_a_retry_button_that_refetches() = runComposeUiTest {
        val repo = FakeReportRepository(listOf(page(0, 1, listOf(report(1, ReportStatus.NEW, "Recovered")))))
            .apply { listError = DomainException.Network }
        content(repo)

        awaitText("Réessayer")
        repo.listError = null
        onNodeWithText("Réessayer").performClick()

        awaitText("Recovered")
    }

    @Test
    fun the_screen_uses_the_sort_passed_from_the_top_bar() = runComposeUiTest {
        val repo = FakeReportRepository(listOf(page(0, 1, listOf(report(1, ReportStatus.NEW, "Un signalement")))))
        content(repo, sort = ReportSort.UNPROCESSED_FIRST)

        awaitText("Un signalement")
        assert(repo.listCalls.last().third == ReportSort.UNPROCESSED_FIRST)
    }

    @Test
    fun the_sort_control_reports_the_chosen_option() = runComposeUiTest {
        var chosen: ReportSort? = null
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    ReportSortControl(current = ReportSort.NEWEST_FIRST, onSelect = { chosen = it })
                }
            }
        }

        onNodeWithContentDescription("Trier").performClick()
        waitForIdle()
        onNodeWithText("Non traités en premier").performClick()
        waitForIdle()

        assert(chosen == ReportSort.UNPROCESSED_FIRST)
    }
}
