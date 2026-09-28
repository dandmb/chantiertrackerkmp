package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.projects.export.ExportSection
import com.dmb.chantiertracker.presentation.projects.export.ProjectExportViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeExportRepository
import com.dmb.chantiertracker.support.FakePdfOpener
import com.dmb.chantiertracker.support.FakePdfSharer
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ExportSectionUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun ComposeUiTest.mount(
        ownerPlan: Plan,
        repo: FakeExportRepository = FakeExportRepository(),
        opener: FakePdfOpener = FakePdfOpener(),
        sharer: FakePdfSharer = FakePdfSharer(),
    ) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        ExportSection(
                            projectLocalId = "p1",
                            ownerPlan = ownerPlan,
                            viewModel = ProjectExportViewModel(repo, opener, sharer),
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun a_semi_flex_owner_sees_the_export_button() = runComposeUiTest {
        mount(Plan.SEMI_FLEX)

        onNodeWithText("Export PDF").assertExists()
        onNodeWithText("Exporter en PDF").assertExists()
        onNodeWithText("Passez à Semi-flex", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_free_owner_sees_the_upgrade_hint_and_no_button() = runComposeUiTest {
        mount(Plan.FREE)

        onNodeWithText("Passez à Semi-flex ou Liberté pour exporter ce projet en PDF.").assertExists()
        onNodeWithText("Exporter en PDF").assertDoesNotExist()
    }

    @Test
    fun clicking_export_generates_and_offers_open_and_share_without_forcing_either() = runComposeUiTest {
        val repo = FakeExportRepository()
        val opener = FakePdfOpener()
        val sharer = FakePdfSharer()
        mount(Plan.LIBERTE, repo, opener, sharer)

        onNodeWithText("Exporter en PDF").performClick()
        waitForIdle()

        assertEquals(listOf("p1"), repo.calls)
        assert(opener.opened.isEmpty()) { "generating the PDF must not open it automatically" }
        assert(sharer.shared.isEmpty()) { "generating the PDF must not share it automatically" }
        onNodeWithText("PDF généré.").assertExists()
        onNodeWithText("Ouvrir").assertExists()
        onNodeWithText("Partager").assertExists()
    }

    @Test
    fun clicking_open_after_export_hands_the_file_to_the_platform_opener() = runComposeUiTest {
        val opener = FakePdfOpener()
        val sharer = FakePdfSharer()
        mount(Plan.LIBERTE, opener = opener, sharer = sharer)

        onNodeWithText("Exporter en PDF").performClick()
        waitForIdle()
        onNodeWithText("Ouvrir").performClick()
        waitForIdle()

        assertEquals(listOf("/cache/exports/chantier-villa-2026-09-08.pdf"), opener.opened)
        assert(sharer.shared.isEmpty())
    }

    @Test
    fun clicking_share_after_export_hands_the_file_to_the_platform_sharer() = runComposeUiTest {
        val opener = FakePdfOpener()
        val sharer = FakePdfSharer()
        mount(Plan.LIBERTE, opener = opener, sharer = sharer)

        onNodeWithText("Exporter en PDF").performClick()
        waitForIdle()
        onNodeWithText("Partager").performClick()
        waitForIdle()

        assertEquals(listOf("/cache/exports/chantier-villa-2026-09-08.pdf"), sharer.shared)
        assert(opener.opened.isEmpty())
    }

    @Test
    fun a_failure_shows_an_error_banner_and_keeps_the_export_button() = runComposeUiTest {
        val repo = FakeExportRepository().apply { error = DomainException.Network }
        mount(Plan.SEMI_FLEX, repo)

        onNodeWithText("Exporter en PDF").performClick()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText("Connexion au serveur impossible", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Exporter en PDF").assertExists()
        onNodeWithText("Ouvrir").assertDoesNotExist()
    }
}
