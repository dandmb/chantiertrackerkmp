package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
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

    private fun snapshot(
        name: String,
        locale: String,
        dark: Boolean = false,
        awaitReady: (ComposeUiTest.() -> Boolean)? = null,
        content: @Composable () -> Unit,
    ) =
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
            // Some screens finish rendering off the compose clock (an image
            // decoded on Dispatchers.Default, say) — waitForIdle() can't see that.
            awaitReady?.let { ready -> waitUntil(timeoutMillis = 5_000L) { ready() }; waitForIdle() }
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

    /** Mirrors MainScreen: the history sort control lives in the detail top bar. */
    @Composable
    private fun ProjectHistoryChrome(title: String) {
        var sort by remember { mutableStateOf(com.dmb.chantiertracker.domain.model.HistorySort.NEWEST_FIRST) }
        Scaffold(
            topBar = {
                DetailTopBar(
                    title = title,
                    onBack = {},
                    actions = {
                        com.dmb.chantiertracker.presentation.projects.history.HistorySortControl(
                            current = sort, onSelect = { sort = it },
                        )
                    },
                )
            },
        ) { padding ->
            com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryScreen(
                projectLocalId = "1", modifier = Modifier.padding(padding), sort = sort,
                viewModel = projectHistoryVm(Plan.FREE),
            )
        }
    }

    /** Mirrors MainScreen: the report sort control lives in the detail top bar. */
    @Composable
    private fun ProjectReportsChrome(title: String) {
        var sort by remember { mutableStateOf(com.dmb.chantiertracker.domain.model.ReportSort.NEWEST_FIRST) }
        Scaffold(
            topBar = {
                DetailTopBar(
                    title = title,
                    onBack = {},
                    actions = {
                        com.dmb.chantiertracker.presentation.reports.ReportSortControl(
                            current = sort, onSelect = { sort = it },
                        )
                    },
                )
            },
        ) { padding ->
            com.dmb.chantiertracker.presentation.reports.ProjectReportsScreen(
                projectLocalId = "1", modifier = Modifier.padding(padding), sort = sort,
                viewModel = projectReportsVm(),
            )
        }
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

    /** Mirrors MainScreen: the detail top-bar title tracks the day date resolved by the screen. */
    @Composable
    private fun DailyLogChrome(fallbackTitle: String, asSupervisorViewingPastDay: Boolean = false) {
        var title by remember { mutableStateOf<String?>(null) }
        Scaffold(
            topBar = { DetailTopBar(title = title ?: fallbackTitle, onBack = {}) },
        ) { padding ->
            DailyLogScreen(
                dailyLogLocalId = "log-1",
                modifier = Modifier.padding(padding),
                onDateResolved = { title = it },
                viewModel = dailyLogVm(asSupervisorViewingPastDay = asSupervisorViewingPastDay),
            )
        }
    }

    private fun projectsVm(
        projects: List<Project>,
        incoming: List<com.dmb.chantiertracker.domain.model.IncomingInvitation> = emptyList(),
    ) = ProjectsViewModel(
        FakeProjectRepository(projects = projects),
        com.dmb.chantiertracker.support.FakeInvitationRepository().apply { this.incoming = incoming },
        ProjectSortHolder(),
    ).also { if (incoming.isNotEmpty()) it.onEnter() }

    private fun settingsVm() = SettingsViewModel(
        AppConfig(FakeBuildInfo(isDebug = false, appVersion = "1.0")),
        com.dmb.chantiertracker.presentation.settings.AppSettings(
            com.dmb.chantiertracker.support.FakeAppPreferences(),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        ),
    )

    private fun authedRepo(globalRole: GlobalRole = GlobalRole.USER) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(1, "jean@chantier.dev", "Jean", true, globalRole)))
    }

    private fun createProjectVm(atLimit: Boolean = false, superAdmin: Boolean = false): CreateProjectViewModel {
        val projects = FakeProjectRepository().apply { if (atLimit) activeProjectCountFlow.value = 1 }
        val account = FakeAccountRepository(
            planUsage = if (atLimit) PlanUsage(Plan.FREE, projectsLimit = 1) else null,
        )
        val role = if (superAdmin) GlobalRole.SUPER_ADMIN else GlobalRole.USER
        return CreateProjectViewModel(projects, account, authedRepo(role))
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
            members = listOf(
                ProjectMember(userId = 1, name = "Jean Marchand", email = "jean@chantier.dev", role = ProjectRole.ADMIN),
                ProjectMember(userId = 2, name = "Sam Ferreira", email = "sam@chantier.dev", role = ProjectRole.SUPERVISOR),
            ),
        )
        val invitations = com.dmb.chantiertracker.support.FakeInvitationRepository(
            listOf(
                Invitation(1, "1", "lea@chantier.dev", ProjectRole.SUPERVISOR, 1L, "2026-09-01T10:00:00", null, InvitationStatus.PENDING),
            ),
        )
        return ProjectDetailViewModel(repo, FakeStageRepository(stages = sampleStages), invitations, auth).also { it.load("1") }
    }

    private fun inviteMemberVm(atLimit: Boolean = false): com.dmb.chantiertracker.presentation.projects.invite.InviteMemberViewModel {
        val repo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = 1L, ownerPlan = if (atLimit) Plan.FREE else Plan.SEMI_FLEX,
            ),
            members = if (atLimit) {
                listOf(ProjectMember(userId = 2, name = "Sam Ferreira", email = "sam@chantier.dev", role = ProjectRole.SUPERVISOR))
            } else {
                emptyList()
            },
        )
        return com.dmb.chantiertracker.presentation.projects.invite.InviteMemberViewModel(
            repo, com.dmb.chantiertracker.support.FakeInvitationRepository(),
        ).also { it.load("1") }
    }

    private fun projectHistoryVm(ownerPlan: Plan): com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryViewModel {
        val repo = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa Vidal", description = null, location = "Nîmes",
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                ownerId = 1L, ownerPlan = ownerPlan,
            ),
        )
        val history = com.dmb.chantiertracker.support.FakeHistoryRepository(
            listOf(
                com.dmb.chantiertracker.domain.model.HistoryPage(
                    items = listOf(
                        historyItem(5, "2026-09-05T14:32:11", "Jean Marchand a modifié le budget prévisionnel de l'étape Gros œuvre : 500 000 → 600 000 EUR"),
                        historyItem(4, "2026-09-04T09:12:03", "Sam Ferreira (superviseur) a ajouté un achat sur l'étape Gros œuvre : 12 sacs de ciment à 42 EUR"),
                        historyItem(3, "2026-09-02T17:45:00", "Sam Ferreira (superviseur) a retiré 3 tonnes de Ciment du stock sur l'étape Fondations"),
                        historyItem(2, "2026-08-30T08:00:00", "Jean Marchand a réactivé le projet Villa Vidal"),
                        historyItem(1, "2026-08-28T11:20:00", "Jean Marchand a créé le projet Villa Vidal"),
                    ),
                    page = 0, totalPages = 3, isFirst = true, isLast = false, totalElements = 45,
                ),
            ),
        )
        return com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryViewModel(history, repo).also { it.load("1") }
    }

    private fun historyItem(id: Long, at: String, description: String) =
        com.dmb.chantiertracker.domain.model.ModificationHistoryItem(
            id = id, modifiedAt = at,
            actionType = com.dmb.chantiertracker.domain.model.HistoryActionType.MODIFICATION,
            description = description, entryId = null, userId = 1L, fieldName = null, oldValue = null, newValue = null,
        )

    private fun projectReportsVm(): com.dmb.chantiertracker.presentation.reports.ProjectReportsViewModel {
        val repo = com.dmb.chantiertracker.support.FakeReportRepository(
            listOf(
                com.dmb.chantiertracker.domain.model.ReportPage(
                    items = listOf(
                        reportItem(
                            id = 3, type = EntryType.PURCHASE, entryDate = "2026-09-04", author = "Sam Ferreira",
                            createdAt = "2026-09-05T08:15:00", message = "La quantité de ciment livrée ne correspond pas au bon de livraison.",
                            status = com.dmb.chantiertracker.domain.model.ReportStatus.NEW, processedAt = null,
                        ),
                        reportItem(
                            id = 2, type = EntryType.WORK, entryDate = "2026-09-02", author = "Sam Ferreira",
                            createdAt = "2026-09-02T18:40:00", message = "Coulage de dalle non mentionné dans le résumé.",
                            status = com.dmb.chantiertracker.domain.model.ReportStatus.PROCESSED, processedAt = "2026-09-03T09:10:00",
                        ),
                    ),
                    page = 0, totalPages = 2, isFirst = true, isLast = false, totalElements = 24,
                ),
            ),
        )
        return com.dmb.chantiertracker.presentation.reports.ProjectReportsViewModel(repo).also { it.load("1") }
    }

    private fun reportItem(
        id: Long, type: EntryType, entryDate: String, author: String?, createdAt: String, message: String,
        status: com.dmb.chantiertracker.domain.model.ReportStatus, processedAt: String?,
    ) = com.dmb.chantiertracker.domain.model.Report(
        id = id, entryId = id * 10, entryType = type, entryDate = entryDate, authorName = author,
        message = message, createdAt = createdAt, status = status, processedAt = processedAt,
    )

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

    private fun dailyLogVm(asSupervisorViewingPastDay: Boolean = false): DailyLogViewModel {
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
                ownerPlan = Plan.SEMI_FLEX,
            ),
            members = if (asSupervisorViewingPastDay) {
                listOf(ProjectMember(userId = 9, name = "Sam Superviseur", email = "sam@chantier.dev", role = ProjectRole.SUPERVISOR))
            } else {
                emptyList()
            },
        )
        val viewerId = if (asSupervisorViewingPastDay) 9L else 1L
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(User(viewerId, "jean@chantier.dev", "Jean Marchand", true, GlobalRole.USER))) }
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
                com.dmb.chantiertracker.domain.model.Attachment(
                    localId = "att2", entryLocalId = "e1", localPath = "/x/clip.mp4",
                    originalName = "livraison.mp4", mimeType = "video/mp4", sizeBytes = 1_200_000L,
                    durationSeconds = 47, uploadedAt = 0L,
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

    private fun reportEntryVm(): com.dmb.chantiertracker.presentation.reports.ReportEntryViewModel =
        com.dmb.chantiertracker.presentation.reports.ReportEntryViewModel(com.dmb.chantiertracker.support.FakeReportRepository())
            .also { it.load("e1"); it.onMessageChange("La quantité de ciment livrée ne correspond pas au bon de livraison.") }

    private fun projectExportVm(): com.dmb.chantiertracker.presentation.projects.export.ProjectExportViewModel =
        com.dmb.chantiertracker.presentation.projects.export.ProjectExportViewModel(
            com.dmb.chantiertracker.support.FakeExportRepository(),
            com.dmb.chantiertracker.support.FakePdfOpener(),
            com.dmb.chantiertracker.support.FakePdfSharer(),
        )

    private fun billingVm(planUsage: com.dmb.chantiertracker.domain.model.PlanUsage?): com.dmb.chantiertracker.presentation.billing.BillingViewModel =
        com.dmb.chantiertracker.presentation.billing.BillingViewModel(
            com.dmb.chantiertracker.support.FakeAccountRepository(planUsage),
            com.dmb.chantiertracker.support.FakeBillingRepository(),
            com.dmb.chantiertracker.support.FakeUrlOpener(),
        )

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
            snapshot("32-projects-incoming-invitation", locale) {
                Chrome(MainTab.Projects) { m ->
                    ProjectsScreen(
                        onProjectClick = {},
                        modifier = m,
                        viewModel = projectsVm(
                            sampleProjects,
                            incoming = listOf(
                                com.dmb.chantiertracker.domain.model.IncomingInvitation(
                                    token = "tok", projectId = 9L, projectName = "Villa Vidal",
                                    role = com.dmb.chantiertracker.domain.model.ProjectRole.SUPERVISOR,
                                    invitedByName = "Jean Marchand",
                                    createdAt = "2026-09-01T10:00:00", expiresAt = null,
                                ),
                            ),
                        ),
                    )
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
            snapshot("40-create-project-super-admin", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Nouveau projet" else "New project",
                ) { m -> CreateProjectScreen(onCreated = {}, modifier = m, viewModel = createProjectVm(superAdmin = true)) }
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
            snapshot(
                "34-project-history",
                locale,
                awaitReady = { onAllNodes(hasText("Villa Vidal", substring = true)).fetchSemanticsNodes().isNotEmpty() },
            ) {
                ProjectHistoryChrome(title = if (locale == "fr") "Historique" else "History")
            }
            snapshot("30-invite-member", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Inviter un superviseur" else "Invite a supervisor",
                ) { m ->
                    com.dmb.chantiertracker.presentation.projects.invite.InviteMemberScreen(
                        projectLocalId = "1", onInvited = {}, modifier = m, viewModel = inviteMemberVm(),
                    )
                }
            }
            snapshot("31-invite-member-limit", locale) {
                DetailChrome(
                    title = if (locale == "fr") "Inviter un superviseur" else "Invite a supervisor",
                ) { m ->
                    com.dmb.chantiertracker.presentation.projects.invite.InviteMemberScreen(
                        projectLocalId = "1", onInvited = {}, modifier = m, viewModel = inviteMemberVm(atLimit = true),
                    )
                }
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
            snapshot(
                "26-daily-log",
                locale,
                // The photo thumbnail decodes off the compose clock (ADR-43) —
                // wait for it before capturing so we snapshot the loaded section.
                awaitReady = {
                    onAllNodes(hasContentDescription("facture-ciment.jpg")).fetchSemanticsNodes().isNotEmpty()
                },
            ) {
                DailyLogChrome(fallbackTitle = if (locale == "fr") "Journée" else "Day")
            }
            snapshot(
                "36-daily-log-supervisor-report",
                locale,
                awaitReady = {
                    onAllNodes(hasContentDescription("facture-ciment.jpg")).fetchSemanticsNodes().isNotEmpty()
                },
            ) {
                DailyLogChrome(
                    fallbackTitle = if (locale == "fr") "Journée" else "Day",
                    asSupervisorViewingPastDay = true,
                )
            }
            snapshot("33-video-player-desktop", locale) {
                DetailChrome(title = if (locale == "fr") "Vidéo" else "Video") { m ->
                    Box(m.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        com.dmb.chantiertracker.presentation.logs.VideoPlayer(localPath = "/x/clip.mp4", modifier = Modifier)
                    }
                }
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
            snapshot("35-report-entry", locale) {
                DetailChrome(title = if (locale == "fr") "Signaler un problème" else "Report an issue") { m ->
                    com.dmb.chantiertracker.presentation.reports.ReportEntryScreen(
                        entryLocalId = "e1", onDone = {}, modifier = m, viewModel = reportEntryVm(),
                    )
                }
            }
            snapshot(
                "37-project-reports",
                locale,
                awaitReady = {
                    onAllNodes(hasText("bon de livraison", substring = true)).fetchSemanticsNodes().isNotEmpty()
                },
            ) {
                ProjectReportsChrome(title = if (locale == "fr") "Signalements" else "Reports")
            }
            snapshot("38-project-export", locale) {
                DetailChrome(title = if (locale == "fr") "Détail du projet" else "Project detail") { m ->
                    Box(m.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        com.dmb.chantiertracker.presentation.projects.export.ExportSection(
                            projectLocalId = "1",
                            ownerPlan = Plan.SEMI_FLEX,
                            viewModel = projectExportVm(),
                        )
                    }
                }
            }
            snapshot("39-billing", locale) {
                DetailChrome(title = if (locale == "fr") "Abonnement" else "Subscription") { m ->
                    com.dmb.chantiertracker.presentation.billing.BillingScreen(
                        modifier = m,
                        viewModel = billingVm(
                            com.dmb.chantiertracker.domain.model.PlanUsage(
                                plan = Plan.SEMI_FLEX,
                                projectsLimit = 3,
                                projectsUsed = 2,
                                photosUsed = 40,
                                photosLimit = 300,
                                videosUsed = 1,
                                videosLimit = 5,
                                videoDurationLimitSeconds = 120,
                                supervisorsUsed = 1,
                                supervisorsLimit = 3,
                                hasStripeCustomer = true,
                            ),
                        ),
                    )
                }
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
