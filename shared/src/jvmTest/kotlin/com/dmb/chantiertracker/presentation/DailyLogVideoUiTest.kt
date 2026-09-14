package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.assertTrue
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.logs.DailyLogScreen
import com.dmb.chantiertracker.presentation.logs.DailyLogViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAttachmentRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeConsumptionLineRepository
import com.dmb.chantiertracker.support.FakeDailyLogRepository
import com.dmb.chantiertracker.support.FakeMaterialRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakePurchaseLineRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DailyLogVideoUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun vm(): DailyLogViewModel {
        val logs = FakeDailyLogRepository(
            detail = DailyLogDetail(
                localId = "log-1", stageLocalId = "s1", date = "2026-09-05",
                entries = listOf(DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "Livraison")),
            ),
        )
        val stages = FakeStageRepository(
            detail = StageDetail(
                localId = "s1", projectLocalId = "p1", name = "Gros œuvre", description = null,
                estimatedBudget = null, startDate = null, endDate = null, status = StageStatus.IN_PROGRESS,
            ),
        )
        val projects = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "p1", name = "Villa", description = null, location = null,
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = 1L, ownerPlan = Plan.SEMI_FLEX,
            ),
        )
        val auth = FakeAuthRepository().apply {
            emitState(AuthState.Authenticated(User(1, "u@x.dev", "U", true, GlobalRole.USER)))
        }
        val attachments = FakeAttachmentRepository(
            attachments = listOf(
                Attachment("a1", "e1", "/x/clip.mp4", "clip.mp4", "video/mp4", 900_000L, durationSeconds = 20, uploadedAt = 0L),
            ),
        )
        return DailyLogViewModel(
            logs, stages, projects, auth, FakeMaterialRepository(),
            FakePurchaseLineRepository(), FakeConsumptionLineRepository(), attachments,
        ).also { it.load("log-1") }
    }

    @Test
    fun tapping_a_video_thumbnail_opens_the_player_dialog() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm()) } }
        }
        waitForIdle()

        onAllNodes(hasContentDescription("Lire la vidéo")).onFirst().performClick()
        waitForIdle()

        // On the JVM the player is the Desktop fallback — its "Ouvrir la vidéo"
        // button proves the tap → dialog → VideoPlayer path is wired.
        onNodeWithText("Ouvrir la vidéo").assertIsDisplayed()
    }

    @Test
    fun the_zoom_dialog_fills_the_screen() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm()) } }
        }
        waitForIdle()
        onAllNodes(hasContentDescription("Lire la vidéo")).onFirst().performClick()
        waitForIdle()

        // The zoom surface should be (near) full-screen — a comfortable viewer,
        // not the small centred card the platform-default Dialog wraps around
        // its content (ADR-39).
        val viewportH = onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.height }
        val zoomH = onNodeWithTag("attachment-zoom").fetchSemanticsNode().size.height
        assertTrue(
            zoomH >= viewportH * 0.8,
            "zoom surface height $zoomH should fill the $viewportH viewport",
        )
    }

    @Test
    fun the_zoom_video_uses_the_available_space() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm()) } }
        }
        waitForIdle()
        onAllNodes(hasContentDescription("Lire la vidéo")).onFirst().performClick()
        waitForIdle()

        // The player region must fill the full-screen dialog, not sit as a small
        // fixed strip in the middle of the black backdrop (ADR-40). Aspect ratio
        // is then the platform player's job (fit, no distortion).
        val dialogH = onNodeWithTag("attachment-zoom", useUnmergedTree = true).fetchSemanticsNode().size.height
        val mediaH = onNodeWithTag("zoom-media", useUnmergedTree = true).fetchSemanticsNode().size.height
        assertTrue(
            mediaH >= dialogH * 0.9,
            "player region $mediaH should fill the $dialogH dialog, not a fixed strip",
        )
    }
}
