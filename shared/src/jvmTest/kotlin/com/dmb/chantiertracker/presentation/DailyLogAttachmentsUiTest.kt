package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
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
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DailyLogAttachmentsUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun tempPng(name: String): String {
        val f = File.createTempFile("test-$name-", ".png").apply { deleteOnExit() }
        val img = java.awt.image.BufferedImage(48, 48, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = java.awt.Color(139, 74, 59); fillRect(0, 0, 48, 48); dispose() }
        ImageIO.write(img, "png", f)
        return f.absolutePath
    }

    private fun vm(vararg attachments: Attachment): DailyLogViewModel {
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
        return DailyLogViewModel(
            logs, stages, projects, auth, FakeMaterialRepository(),
            FakePurchaseLineRepository(), FakeConsumptionLineRepository(),
            FakeAttachmentRepository(attachments = attachments.toList()),
        ).also { it.load("log-1") }
    }

    @Test
    fun photo_thumbnails_are_revealed_together_and_the_indicator_then_clears() = runComposeUiTest {
        val photos = arrayOf(
            Attachment("p1", "e1", tempPng("thumb1"), "recu-1.jpg", "image/jpeg", 1L, uploadedAt = 0L),
            Attachment("p2", "e1", tempPng("thumb2"), "recu-2.jpg", "image/jpeg", 1L, uploadedAt = 0L),
        )
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm(*photos)) } }
        }

        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasContentDescription("recu-1.jpg")).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodes(hasContentDescription("recu-2.jpg")).fetchSemanticsNodes().isNotEmpty()
        }
        // Both thumbnails appear together, and the global indicator is gone.
        onNodeWithText("Chargement des justificatifs…").assertDoesNotExist()
    }

    @Test
    fun a_photo_whose_file_is_missing_never_hangs_the_indicator() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    DailyLogScreen(
                        dailyLogLocalId = "log-1",
                        viewModel = vm(
                            Attachment("p1", "e1", tempPng("ok"), "ok.jpg", "image/jpeg", 1L, uploadedAt = 0L),
                            Attachment("p2", "e1", "/does/not/exist.jpg", "gone.jpg", "image/jpeg", 1L, uploadedAt = 0L),
                        ),
                    )
                }
            }
        }

        // A failed decode still counts as "done" — offline-first, no network wait.
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText("Chargement des justificatifs…")).fetchSemanticsNodes().isEmpty()
        }
        onAllNodes(hasContentDescription("ok.jpg")).onFirst().assertIsDisplayed()
    }
}
