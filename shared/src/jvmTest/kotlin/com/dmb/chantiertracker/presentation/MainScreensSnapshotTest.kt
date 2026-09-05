package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AccountMenuBody
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.AppBottomBar
import com.dmb.chantiertracker.presentation.main.AppTopBar
import com.dmb.chantiertracker.presentation.main.DetailTopBar
import com.dmb.chantiertracker.presentation.main.MainTab
import com.dmb.chantiertracker.presentation.projects.ProjectSortControl
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectSortMenuItems
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.ProjectsViewModel
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectScreen
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectViewModel
import com.dmb.chantiertracker.presentation.projects.detail.DeleteProjectDialog
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailViewModel
import com.dmb.chantiertracker.presentation.projects.edit.EditProjectScreen
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.presentation.settings.SettingsViewModel
import com.dmb.chantiertracker.presentation.stages.create.CreateStageScreen
import com.dmb.chantiertracker.presentation.stages.create.CreateStageViewModel
import com.dmb.chantiertracker.presentation.logs.DailyLogScreen
import com.dmb.chantiertracker.presentation.logs.DailyLogViewModel
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailScreen
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeAttachmentRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeConsumptionLineRepository
import com.dmb.chantiertracker.support.FakeDailyLogRepository
import com.dmb.chantiertracker.support.FakeMaterialRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakePurchaseLineRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import kotlinx.datetime.LocalDate
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class MainScreensSnapshotTest {

    private val outDir = File("build/auth-snapshots").apply { mkdirs() }

    private val sampleProjects = listOf(
        Project("1", "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS),
        Project("2", "Hangar logistique Est", null, "Béziers", ProjectStatus.SUSPENDED),
        Project("3", "Réfection toiture Marchand", null, null, ProjectStatus.COMPLETED),
    )

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun snapshot(name: String, locale: String, dark: Boolean = false, content: @Composable () -> Unit) =
        runComposeUiTest {
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme(darkTheme = dark) {
                        Box(Modifier.size(412.dp, 892.dp)) { content() }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(outDir, "$name-$locale.png"))
        }

    private fun dialogSnapshot(name: String, locale: String, content: @Composable () -> Unit) =
        runComposeUiTest {
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme(darkTheme = false) {
                        Box(Modifier.size(412.dp, 892.dp)) { content() }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(
                onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(),
                "png",
                File(outDir, "$name-$locale.png"),
            )
        }

    @Composable
    private fun Chrome(tab: MainTab, screen: @Composable (Modifier) -> Unit) {
        Scaffold(
            topBar = {
                AppTopBar(
                    userName = "Jean Marchand",
                    email = "jean@chantier.dev",
                    plan = Plan.LIBERTE,
                    onSubscription = {},
                    onLogout = {},
                    leadingActions = {
                        if (tab == MainTab.Projects) {
                            ProjectSortControl(current = ProjectSort.NEWEST_FIRST, onSelect = {})
                        }
                    },
                )
            },
            bottomBar = { AppBottomBar(current = tab, onSelect = {}) },
            floatingActionButton = {
                if (tab == MainTab.Projects) {
                    FloatingActionButton(onClick = {}) { Icon(AddIcon, contentDescription = null) }
                }
            },
        ) { padding -> screen(Modifier.padding(padding)) }
    }

    @Composable
    private fun DetailChrome(title: String, screen: @Composable (Modifier) -> Unit) {
        Scaffold(
            topBar = { DetailTopBar(title = title, onBack = {}) },
        ) { padding -> screen(Modifier.padding(padding)) }
    }

    /** Mirrors MainScreen: the detail top-bar title tracks the project name resolved by the screen. */
    @Composable
    private fun ProjectDetailChrome(fallbackTitle: String, projectVm: ProjectDetailViewModel) {
        var title by remember { mutableStateOf<String?>(null) }
        Scaffold(
            topBar = { DetailTopBar(title = title ?: fallbackTitle, onBack = {}) },
        ) { padding ->
            ProjectDetailScreen(
                projectLocalId = "1",
                modifier = Modifier.padding(padding),
                onProjectNameResolved = { title = it },
                viewModel = projectVm,
            )
        }
    }

    private fun projectsVm(projects: List<Project>) =
        ProjectsViewModel(FakeProjectRepository(projects = projects), ProjectSortHolder())

    private fun settingsVm() = SettingsViewModel(AppConfig(FakeBuildInfo(isDebug = false, appVersion = "1.0")))

    private fun authedRepo() = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(1, "jean@chantier.dev", "Jean", true, GlobalRole.USER)))
    }

    private fun createProjectVm(atLimit: Boolean = false): CreateProjectViewModel {
        val projects = FakeProjectRepository().apply { if (atLimit) activeProjectCountFlow.value = 1 }
        val account = FakeAccountRepository(
            planUsage = if (atLimit) PlanUsage(Plan.FREE, projectsLimit = 1) else null,
        )
        return CreateProjectViewModel(projects, account, authedRepo())
    }

    private val sampleStages = listOf(
        Stage("s1", "1", "Gros œuvre", 18000.0, StageStatus.IN_PROGRESS),
        Stage("s2", "1", "Toiture", null, StageStatus.COMPLETED),
    )

    private fun detailVm(canEdit: Boolean): ProjectDetailViewModel {
        val user = User(1, "jean@chantier.dev", "Jean Marchand", true, GlobalRole.USER)
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(user)) }
        val repo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1",
                name = "Villa Vidal",
                description = "Construction d'une villa individuelle avec piscine et pool house.",
                location = "Nîmes",
                currency = "EUR",
                timezone = "Europe/Paris",
                status = ProjectStatus.IN_PROGRESS,
                ownerId = if (canEdit) 1L else 999L,
            ),
        )
        return ProjectDetailViewModel(repo, FakeStageRepository(stages = sampleStages), auth).also { it.load("1") }
    }

    private fun editProjectVm(): com.dmb.chantiertracker.presentation.projects.edit.EditProjectViewModel {
        val repo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1",
                name = "Villa Vidal",
                description = "Construction d'une villa individuelle avec piscine et pool house.",
                location = "Nîmes",
                currency = "EUR",
                timezone = "Europe/Paris",
                status = ProjectStatus.IN_PROGRESS,
                ownerId = 1L,
            ),
        )
        return com.dmb.chantiertracker.presentation.projects.edit.EditProjectViewModel(repo).also { it.load("1") }
    }

    private fun createStageVm(canSetBudget: Boolean): CreateStageViewModel {
        val user = User(1, "jean@chantier.dev", "Jean Marchand", true, GlobalRole.USER)
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(user)) }
        val projectRepo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = if (canSetBudget) 1L else 999L,
            ),
        )
        return CreateStageViewModel(FakeStageRepository(), projectRepo, auth).also { it.start("1") }
    }

    private fun stageDetailVm(withBudget: Boolean = true): StageDetailViewModel {
        val repo = FakeStageRepository(
            detail = StageDetail(
                localId = "s1", projectLocalId = "1", name = "Gros œuvre",
                description = "Fondations, dalle, élévation des murs porteurs.",
                estimatedBudget = if (withBudget) 18000.0 else null,
                startDate = if (withBudget) "2026-02-01" else null,
                endDate = if (withBudget) "2026-05-15" else null,
                status = StageStatus.IN_PROGRESS,
            ),
        )
        val projectRepo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = 1L,
            ),
        )
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(1, "jean@chantier.dev", "Jean Marchand", true, GlobalRole.USER))) }
        val logs = FakeDailyLogRepository(
            logs = listOf(
                DailyLog("log-1", "s1", "2026-09-04", hasPurchase = true, hasWork = true),
                DailyLog("log-2", "s1", "2026-09-03", hasPurchase = true, hasWork = false),
            ),
        )
        return StageDetailViewModel(repo, projectRepo, logs, auth).also { it.load("s1") }
    }

    private fun dailyLogVm(): DailyLogViewModel {
        val stageRepo = FakeStageRepository(
            detail = StageDetail(
                localId = "s1", projectLocalId = "1", name = "Gros œuvre", description = null,
                estimatedBudget = 18000.0, startDate = "2026-02-01", endDate = "2026-05-15",
                status = StageStatus.IN_PROGRESS,
            ),
        )
        val projectRepo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = 1L,
            ),
        )
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(1, "jean@chantier.dev", "Jean Marchand", true, GlobalRole.USER))) }
        val logs = FakeDailyLogRepository(
            detail = DailyLogDetail(
                localId = "log-1", stageLocalId = "s1", date = "2026-09-04",
                entries = listOf(
                    DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "12 sacs de ciment livrés"),
                    DailyEntry("e2", "log-1", EntryType.WORK, summary = "Coulage de la dalle"),
                ),
            ),
        )
        val ciment = com.dmb.chantiertracker.domain.model.Material("m1", "1", "Ciment", "sac")
        val fer = com.dmb.chantiertracker.domain.model.Material("m2", "1", "Fer", "barre")
        val materials = FakeMaterialRepository(
            materials = listOf(ciment, fer),
            stock = listOf(
                com.dmb.chantiertracker.domain.model.MaterialStock("m1", "Ciment", "sac", quantityIn = 12.0, quantityOut = 4.0),
                com.dmb.chantiertracker.domain.model.MaterialStock("m2", "Fer", "barre", quantityIn = 0.0, quantityOut = 0.0),
            ),
        )
        val purchaseLines = FakePurchaseLineRepository(
            lines = listOf(
                com.dmb.chantiertracker.domain.model.PurchaseLine("pl1", "e1", "m1", quantity = 12.0, unitPrice = 3.5, totalPrice = 42.0, supplier = "Quincaillerie du Port"),
            ),
        )
        val consumptionLines = FakeConsumptionLineRepository(
            lines = listOf(com.dmb.chantiertracker.domain.model.ConsumptionLine("cl1", "e2", "m1", quantity = 4.0)),
        )
        val attachments = FakeAttachmentRepository(
            attachments = listOf(
                com.dmb.chantiertracker.domain.model.Attachment(
                    localId = "att1", entryLocalId = "e1", localPath = sampleAttachmentPath(),
                    originalName = "facture-ciment.jpg", mimeType = "image/jpeg", sizeBytes = 2_048L, uploadedAt = 0L,
                ),
            ),
        )
        return DailyLogViewModel(logs, stageRepo, projectRepo, auth, materials, purchaseLines, consumptionLines, attachments).also { it.load("log-1") }
    }

    // A real (tiny, solid-color) PNG on disk — the snapshot exercises the actual
    // decodeToImageBitmap() pipeline instead of falling back to the placeholder,
    // so it visually confirms a real photo renders, not just the empty-state layout.
    private fun sampleAttachmentPath(): String {
        val file = File.createTempFile("snapshot-attachment", ".png").apply { deleteOnExit() }
        val image = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = java.awt.Color(139, 74, 59)
            fillRect(0, 0, 64, 64)
            dispose()
        }
        ImageIO.write(image, "png", file)
        return file.absolutePath
    }

    private fun entrySummaryVm(): com.dmb.chantiertracker.presentation.logs.EntrySummaryViewModel {
        val repo = FakeDailyLogRepository().apply {
            entryFlow.value = DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "12 sacs de ciment livrés")
        }
        return com.dmb.chantiertracker.presentation.logs.EntrySummaryViewModel(repo).also { it.load("e1") }
    }

    private fun purchaseLineFormVm(): com.dmb.chantiertracker.presentation.logs.PurchaseLineFormViewModel {
        val materials = FakeMaterialRepository(
            materials = listOf(
                com.dmb.chantiertracker.domain.model.Material("m1", "1", "Ciment", "sac"),
                com.dmb.chantiertracker.domain.model.Material("m2", "1", "Fer", "barre"),
            ),
        )
        return com.dmb.chantiertracker.presentation.logs.PurchaseLineFormViewModel(materials, FakePurchaseLineRepository())
            .also { it.load("e1", "1", null) }
    }

    private fun consumptionLineFormVm(): com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormViewModel {
        val materials = FakeMaterialRepository(
            stock = listOf(
                com.dmb.chantiertracker.domain.model.MaterialStock("m1", "Ciment", "sac", quantityIn = 12.0, quantityOut = 4.0),
                com.dmb.chantiertracker.domain.model.MaterialStock("m2", "Fer", "barre", quantityIn = 6.0, quantityOut = 0.0),
            ),
        )
        return com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormViewModel(materials, FakeConsumptionLineRepository())
            .also { it.load("e2", "1", null); it.selectMaterial("m1"); it.onQuantityChange("4") }
    }

    @Test
    fun capture_main_screens_in_french_and_english() {
        for (locale in listOf("fr", "en")) {
            snapshot("10-projects-list", locale) {
                Chrome(MainTab.Projects) { m ->
                    ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(sampleProjects))
                }
            }
            snapshot("11-projects-empty", locale) {
                Chrome(MainTab.Projects) { m ->
                    ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(emptyList()))
                }
            }
            snapshot("12-settings", locale) {
                Chrome(MainTab.Settings) { m -> SettingsScreen(modifier = m, viewModel = settingsVm()) }
            }
        }
        snapshot("13-projects-list-dark", "fr", dark = true) {
            Chrome(MainTab.Projects) { m ->
                ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(sampleProjects))
            }
        }
        for (locale in listOf("fr", "en")) {
            snapshot("21-staging-banner", locale) {
                AppChrome(showStagingBanner = true) {
                    Chrome(MainTab.Projects) { m ->
                        ProjectsScreen(onProjectClick = {}, modifier = m, viewModel = projectsVm(sampleProjects))
                    }
                }
            }
        }
        for (locale in listOf("fr", "en")) {
            snapshot("14-create-project", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Nouveau projet" else "New project",
                ) { m -> CreateProjectScreen(onCreated = {}, modifier = m, viewModel = createProjectVm()) }
            }
            snapshot("23-create-project-plan-limit", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Nouveau projet" else "New project",
                ) { m -> CreateProjectScreen(onCreated = {}, modifier = m, viewModel = createProjectVm(atLimit = true)) }
            }
            snapshot("16-project-detail", locale) {
                ProjectDetailChrome(
                    fallbackTitle = if (locale == "fr") "Projet" else "Project",
                    projectVm = detailVm(canEdit = true),
                )
            }
            snapshot("24-edit-project", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Modifier le projet" else "Edit project",
                ) { m -> EditProjectScreen(projectLocalId = "1", onSaved = {}, onBack = {}, modifier = m, viewModel = editProjectVm()) }
            }
            dialogSnapshot("25-delete-project-dialog", locale) {
                DeleteProjectDialog(projectName = "Villa Vidal", onDismiss = {}, onConfirm = {})
            }
            snapshot("18-create-stage", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Nouvelle étape" else "New stage",
                ) { m -> CreateStageScreen(projectLocalId = "1", onCreated = {}, modifier = m, viewModel = createStageVm(canSetBudget = true)) }
            }
            snapshot("19-stage-detail", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Étape" else "Stage",
                ) { m -> StageDetailScreen(stageLocalId = "s1", modifier = m, viewModel = stageDetailVm()) }
            }
            snapshot("20-stage-detail-no-budget", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Étape" else "Stage",
                ) { m -> StageDetailScreen(stageLocalId = "s1", modifier = m, viewModel = stageDetailVm(withBudget = false)) }
            }
            snapshot("26-daily-log", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Journée" else "Day",
                ) { m -> DailyLogScreen(dailyLogLocalId = "log-1", modifier = m, viewModel = dailyLogVm()) }
            }
            snapshot("27-entry-summary", locale) {
                DetailChrome(title = if (locale == "fr") "Modifier le résumé" else "Edit summary") { m ->
                    com.dmb.chantiertracker.presentation.logs.EntrySummaryScreen(
                        entryLocalId = "e1", onSaved = {}, onBack = {}, modifier = m, viewModel = entrySummaryVm(),
                    )
                }
            }
            snapshot("28-purchase-line-form", locale) {
                DetailChrome(title = if (locale == "fr") "Ajouter un article" else "Add an item") { m ->
                    com.dmb.chantiertracker.presentation.logs.PurchaseLineFormScreen(
                        entryLocalId = "e1", projectLocalId = "1", lineLocalId = null, currency = "EUR",
                        onSaved = {}, onBack = {}, modifier = m, viewModel = purchaseLineFormVm(),
                    )
                }
            }
            snapshot("29-consumption-line-form", locale) {
                DetailChrome(title = if (locale == "fr") "Ajouter un matériau consommé" else "Add a consumed material") { m ->
                    com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormScreen(
                        entryLocalId = "e2", projectLocalId = "1", lineLocalId = null,
                        onSaved = {}, onBack = {}, modifier = m, viewModel = consumptionLineFormVm(),
                    )
                }
            }
            snapshot("22-stage-date-picker", locale) {
                Box(Modifier.padding(16.dp)) { StageDatePickerPreview() }
            }
        }
        for (locale in listOf("fr", "en")) {
            snapshot("15-account-menu", locale) {
                MenuSurface {
                    AccountMenuBody(
                        userName = "Jean Marchand",
                        email = "jean@chantier.dev",
                        plan = Plan.LIBERTE,
                        onSubscription = {},
                        onLogout = {},
                    )
                }
            }
            snapshot("17-project-sort", locale) {
                MenuSurface {
                    ProjectSortMenuItems(current = ProjectSort.NEWEST_FIRST, onSelect = {})
                }
            }
        }
    }

    @Composable
    private fun MenuSurface(content: @Composable () -> Unit) {
        Box(Modifier.padding(12.dp)) {
            Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 3.dp, shadowElevation = 3.dp) {
                Column(Modifier.width(260.dp)) { content() }
            }
        }
    }

    /** The calendar that DateField opens, floored at a fixed "today" so the past days read as disabled. */
    @Composable
    private fun StageDatePickerPreview() {
        val floorMillis = LocalDate(2026, 9, 4).toUtcMillis()
        val state = rememberDatePickerState(
            initialDisplayedMonthMillis = floorMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= floorMillis
                override fun isSelectableYear(year: Int) = year >= 2026
            },
        )
        DatePicker(state = state, showModeToggle = false)
    }
}
