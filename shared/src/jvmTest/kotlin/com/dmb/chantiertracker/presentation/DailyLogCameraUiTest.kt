package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
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
import com.dmb.chantiertracker.presentation.logs.LocalPhotoCapture
import com.dmb.chantiertracker.presentation.logs.PhotoCapture
import com.dmb.chantiertracker.presentation.logs.PhotoCaptureFactory
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
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DailyLogCameraUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).toString()
    private val attachments = FakeAttachmentRepository()
    private var launches = 0

    private fun takenPhoto(): File {
        val file = File.createTempFile("camera-", ".jpg").apply { deleteOnExit() }
        val image = java.awt.image.BufferedImage(64, 48, java.awt.image.BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "jpg", file)
        return file
    }

    private fun cameraReturning(photo: File?) = object : PhotoCaptureFactory {
        @Composable
        override fun rememberCapture(onCaptured: (PlatformFile?) -> Unit): PhotoCapture = PhotoCapture {
            launches++
            onCaptured(photo?.let { PlatformFile(it) })
        }
    }

    private fun viewModel(): DailyLogViewModel {
        val logs = FakeDailyLogRepository(
            detail = DailyLogDetail(
                localId = "log-1", stageLocalId = "s1", date = today,
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
            emitState(AuthState.Authenticated(User(1L, "u@x.dev", "U", true, GlobalRole.USER)))
        }
        return DailyLogViewModel(
            logs, stages, projects, auth, FakeMaterialRepository(),
            FakePurchaseLineRepository(), FakeConsumptionLineRepository(), attachments,
        ).also { it.load("log-1") }
    }

    private fun ComposeUiTest.mount(camera: PhotoCaptureFactory?, locale: String = "fr") {
        val viewModel = viewModel()
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    if (camera == null) {
                        DailyLogScreen(dailyLogLocalId = "log-1", viewModel = viewModel)
                    } else {
                        CompositionLocalProvider(LocalPhotoCapture provides camera) {
                            DailyLogScreen(dailyLogLocalId = "log-1", viewModel = viewModel)
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun take_a_photo_sits_next_to_the_gallery_and_the_photo_joins_the_same_upload_path() = runComposeUiTest {
        val photo = takenPhoto()
        mount(cameraReturning(photo))

        onNodeWithText("Ajouter une photo").performScrollTo().assertIsDisplayed()
        onNodeWithText("Prendre une photo").performScrollTo().assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(1, launches)
        assertEquals(listOf("addAttachment:e1:${photo.name}:image/jpeg:${photo.length()}"), attachments.log)
    }

    @Test
    fun in_english_the_button_reads_take_a_photo() = runComposeUiTest {
        mount(cameraReturning(takenPhoto()), locale = "en")

        onNodeWithText("Take a photo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun closing_the_camera_without_a_photo_adds_nothing_and_shows_no_error() = runComposeUiTest {
        mount(cameraReturning(null))

        onNodeWithText("Prendre une photo").performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, launches)
        assertEquals(emptyList(), attachments.log)
        onNodeWithText("Impossible d'ajouter cette photo.").assertDoesNotExist()
    }

    @Test
    fun a_device_without_a_camera_intent_shows_only_the_gallery() = runComposeUiTest {
        mount(camera = null)

        onNodeWithText("Ajouter une photo").performScrollTo().assertIsDisplayed()
        onNodeWithText("Prendre une photo").assertDoesNotExist()
    }
}
