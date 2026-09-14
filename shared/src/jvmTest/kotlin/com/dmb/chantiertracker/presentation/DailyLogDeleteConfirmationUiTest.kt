package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
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
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.PurchaseLine
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ADR-54 point 3/5: none of these three deletions used to ask for
// confirmation — a stray tap on the delete icon fired the repository call
// immediately. These tests pin the new behaviour: the tap only opens
// ConfirmActionDialog, the repository is untouched until the dialog's own
// confirm button is pressed.
@OptIn(ExperimentalTestApi::class)
class DailyLogDeleteConfirmationUiTest {

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

    private fun vm(
        purchaseLineRepo: FakePurchaseLineRepository = FakePurchaseLineRepository(),
        consumptionLineRepo: FakeConsumptionLineRepository = FakeConsumptionLineRepository(),
        attachmentRepo: FakeAttachmentRepository = FakeAttachmentRepository(),
    ): DailyLogViewModel {
        val logs = FakeDailyLogRepository(
            detail = DailyLogDetail(
                localId = "log-1", stageLocalId = "s1", date = "2026-09-05",
                entries = listOf(
                    DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "Livraison"),
                    DailyEntry("e2", "log-1", EntryType.WORK, summary = "Coulage"),
                ),
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
                // owner == the signed-in user below, so isAdmin is true and the
                // delete icons (ADMIN-only) render.
                ownerId = 1L, ownerPlan = Plan.SEMI_FLEX,
            ),
        )
        val auth = FakeAuthRepository().apply {
            emitState(AuthState.Authenticated(User(1, "u@x.dev", "U", true, GlobalRole.USER)))
        }
        val materials = FakeMaterialRepository(
            materials = listOf(Material("mat-cement", "p1", "Ciment", "sac")),
            stock = listOf(MaterialStock("mat-sand", "Sable", "kg", quantityIn = 500.0, quantityOut = 0.0)),
        )
        return DailyLogViewModel(
            logs, stages, projects, auth, materials, purchaseLineRepo, consumptionLineRepo, attachmentRepo,
        ).also { it.load("log-1") }
    }

    @Test
    fun deleting_a_purchase_line_requires_confirmation() = runComposeUiTest {
        val purchaseLineRepo = FakePurchaseLineRepository(
            listOf(PurchaseLine("pl1", "e1", "mat-cement", 10.0, 5.0, 50.0, supplier = null)),
        )
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm(purchaseLineRepo = purchaseLineRepo)) } }
        }
        waitForIdle()

        onNodeWithContentDescription("Supprimer").performClick()
        waitForIdle()

        assertTrue(purchaseLineRepo.log.isEmpty(), "la suppression ne doit pas s'exécuter avant confirmation")
        onNodeWithText("Supprimer cet article ?").assertIsDisplayed()
        onNodeWithText("Ciment sera définitivement retiré de cet achat.").assertIsDisplayed()

        onNodeWithText("Supprimer").performClick()
        waitForIdle()

        assertEquals(listOf("deleteLine:pl1"), purchaseLineRepo.log)
    }

    @Test
    fun deleting_a_consumption_line_requires_confirmation() = runComposeUiTest {
        val consumptionLineRepo = FakeConsumptionLineRepository(
            listOf(ConsumptionLine("cl1", "e2", "mat-sand", 20.0)),
        )
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm(consumptionLineRepo = consumptionLineRepo)) } }
        }
        waitForIdle()

        onNodeWithContentDescription("Supprimer").performClick()
        waitForIdle()

        assertTrue(consumptionLineRepo.log.isEmpty(), "la suppression ne doit pas s'exécuter avant confirmation")
        onNodeWithText("Supprimer ce matériau ?").assertIsDisplayed()

        onNodeWithText("Supprimer").performClick()
        waitForIdle()

        assertEquals(listOf("deleteLine:cl1"), consumptionLineRepo.log)
    }

    @Test
    fun deleting_an_attachment_requires_confirmation() = runComposeUiTest {
        val attachmentRepo = FakeAttachmentRepository(
            listOf(Attachment("a1", "e1", tempPng("recu"), "recu.jpg", "image/jpeg", 1L, uploadedAt = 0L)),
        )
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { DailyLogScreen(dailyLogLocalId = "log-1", viewModel = vm(attachmentRepo = attachmentRepo)) } }
        }
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasContentDescription("recu.jpg")).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("Supprimer la photo").performClick()
        waitForIdle()

        assertTrue(attachmentRepo.log.none { it.startsWith("delete") }, "la suppression ne doit pas s'exécuter avant confirmation")
        onNodeWithText("Supprimer ce justificatif ?").assertIsDisplayed()

        onNodeWithText("Supprimer la photo").performClick()
        waitForIdle()

        assertTrue(attachmentRepo.log.any { it.startsWith("delete") && it.contains("a1") })
    }
}
