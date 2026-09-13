package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.presentation.admin.AdminCreateUserScreen
import com.dmb.chantiertracker.presentation.admin.AdminStatsGranularityControl
import com.dmb.chantiertracker.presentation.admin.AdminStatsScreen
import com.dmb.chantiertracker.presentation.admin.AdminUsersScreen
import com.dmb.chantiertracker.presentation.billing.BillingScreen
import com.dmb.chantiertracker.presentation.billing.CheckoutDeepLink
import com.dmb.chantiertracker.presentation.billing.CheckoutDeepLinkDispatcher
import com.dmb.chantiertracker.presentation.navigation.AdminCreateUserRoute
import com.dmb.chantiertracker.presentation.navigation.AdminStatsRoute
import com.dmb.chantiertracker.presentation.navigation.AdminUsersRoute
import com.dmb.chantiertracker.presentation.navigation.BillingNotice
import com.dmb.chantiertracker.presentation.navigation.BillingRoute
import com.dmb.chantiertracker.presentation.navigation.ConsumptionLineFormRoute
import com.dmb.chantiertracker.presentation.navigation.CreateProjectRoute
import com.dmb.chantiertracker.presentation.navigation.CreateStageRoute
import com.dmb.chantiertracker.presentation.navigation.DailyLogRoute
import com.dmb.chantiertracker.presentation.navigation.EditProjectRoute
import com.dmb.chantiertracker.presentation.navigation.EntrySummaryRoute
import com.dmb.chantiertracker.presentation.navigation.InviteMemberRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectDetailRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectHistoryRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectReportsRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectsRoute
import com.dmb.chantiertracker.presentation.navigation.PurchaseLineFormRoute
import com.dmb.chantiertracker.presentation.navigation.ReportEntryRoute
import com.dmb.chantiertracker.presentation.navigation.SettingsRoute
import com.dmb.chantiertracker.presentation.navigation.StageDetailRoute
import com.dmb.chantiertracker.presentation.logs.ConsumptionLineFormScreen
import com.dmb.chantiertracker.presentation.logs.DailyLogScreen
import com.dmb.chantiertracker.presentation.logs.EntrySummaryScreen
import com.dmb.chantiertracker.presentation.logs.PurchaseLineFormScreen
import com.dmb.chantiertracker.presentation.reports.ReportEntryScreen
import com.dmb.chantiertracker.presentation.projects.ProjectSortControl
import com.dmb.chantiertracker.presentation.projects.ProjectSortHolder
import com.dmb.chantiertracker.presentation.projects.ProjectsScreen
import com.dmb.chantiertracker.presentation.projects.create.CreateProjectScreen
import com.dmb.chantiertracker.presentation.projects.detail.ProjectDetailScreen
import com.dmb.chantiertracker.presentation.projects.edit.EditProjectScreen
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.presentation.projects.history.HistorySortControl
import com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryScreen
import com.dmb.chantiertracker.presentation.reports.ProjectReportsScreen
import com.dmb.chantiertracker.presentation.reports.ReportSortControl
import com.dmb.chantiertracker.presentation.projects.invite.InviteMemberScreen
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.presentation.stages.create.CreateStageScreen
import com.dmb.chantiertracker.presentation.stages.detail.StageDetailScreen
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_create_user_title
import com.dmb.chantiertracker.resources.admin_users_create
import com.dmb.chantiertracker.resources.admin_users_title
import com.dmb.chantiertracker.resources.billing_title
import com.dmb.chantiertracker.resources.create_project_title
import com.dmb.chantiertracker.resources.create_stage_title
import com.dmb.chantiertracker.resources.daily_log_title
import com.dmb.chantiertracker.resources.detail_title
import com.dmb.chantiertracker.resources.edit_project_title
import com.dmb.chantiertracker.resources.history_title
import com.dmb.chantiertracker.resources.invite_member_title
import com.dmb.chantiertracker.resources.projects_new
import com.dmb.chantiertracker.resources.report_entry_title
import com.dmb.chantiertracker.resources.reports_title
import com.dmb.chantiertracker.resources.stage_detail_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class MainDestination {
    Projects, Administration, AdminUsers, AdminCreateUser, Settings, CreateProject, ProjectDetail, EditProject, InviteMember, ProjectHistory, ProjectReports, CreateStage, StageDetail, DailyLog,
    EntrySummary, PurchaseLineForm, ConsumptionLineForm, ReportEntry, Billing
}

// ADR-52 — a SUPER_ADMIN can neither own nor join a project (blocked
// upstream, ADR-25 correction): the Projects tab would be permanently dead
// for that account, so it is replaced by Administration rather than added
// as a 3rd tab next to one it could never use. Read once, from a value
// AuthState.Authenticated already carries synchronously (see RootNavHost) —
// NavHost's startDestination is only ever evaluated on first composition.
// Sous-étape 4/4 — the tab now lands on the stats dashboard (AdminStatsRoute),
// mirroring the real web's own /admin root; AdminUsersRoute (the sub-step
// 1/4 placeholder) is reached from there via a nav row, no longer the
// tab's own landing.
private fun startDestinationFor(globalRole: GlobalRole): Any =
    if (globalRole == GlobalRole.SUPER_ADMIN) AdminStatsRoute else ProjectsRoute

private fun tabsFor(globalRole: GlobalRole): List<MainTab> =
    if (globalRole == GlobalRole.SUPER_ADMIN) {
        listOf(MainTab.Administration, MainTab.Settings)
    } else {
        listOf(MainTab.Projects, MainTab.Settings)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(globalRole: GlobalRole, viewModel: MainViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val account by viewModel.state.collectAsStateWithLifecycle()
    val startDestination = remember(globalRole) { startDestinationFor(globalRole) }
    val tabs = remember(globalRole) { tabsFor(globalRole) }

    // ADR-51 point 4 — a chantiertracker:// deep link (Stripe checkout/portal
    // return) can arrive at any time, independent of whatever is currently on
    // screen; this dispatcher is the single funnel platform code (Android
    // onNewIntent, iOS onOpenURL) feeds into. BillingScreen itself already
    // refreshes on entry (BillingViewModel.init), so simply landing on
    // BillingRoute is all that's needed beyond the one-shot success banner.
    val deepLinkDispatcher = koinInject<CheckoutDeepLinkDispatcher>()
    val pendingDeepLink by deepLinkDispatcher.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLink) {
        val notice = when (pendingDeepLink) {
            CheckoutDeepLink.CheckoutSuccess -> BillingNotice.CheckoutSucceeded.toArg()
            CheckoutDeepLink.CheckoutCancelled, CheckoutDeepLink.PortalReturn -> null
            null -> return@LaunchedEffect
        }
        // popUpTo(...) { inclusive = true } + launchSingleTop — replaces an
        // existing BillingRoute entry instead of stacking a second one on
        // top of it. The common case is landing here from BillingRoute
        // itself ("Gérer mon abonnement" lives on that screen): without
        // this, the deep link would push a duplicate, invisible entry
        // (same screen rendered twice in a row) that silently adds one more
        // required back-press before really leaving the screen — see
        // retour-checkout-stripe.md for the device-confirmed bug this fed
        // into (back landing on the leftover browser tab).
        navController.navigate(BillingRoute(notice)) {
            popUpTo(BillingRoute::class) { inclusive = true }
            launchSingleTop = true
        }
        deepLinkDispatcher.consume()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val current = when {
        destination?.hasRoute(AdminStatsRoute::class) == true -> MainDestination.Administration
        destination?.hasRoute(AdminUsersRoute::class) == true -> MainDestination.AdminUsers
        destination?.hasRoute(AdminCreateUserRoute::class) == true -> MainDestination.AdminCreateUser
        destination?.hasRoute(SettingsRoute::class) == true -> MainDestination.Settings
        destination?.hasRoute(CreateProjectRoute::class) == true -> MainDestination.CreateProject
        destination?.hasRoute(ProjectDetailRoute::class) == true -> MainDestination.ProjectDetail
        destination?.hasRoute(EditProjectRoute::class) == true -> MainDestination.EditProject
        destination?.hasRoute(InviteMemberRoute::class) == true -> MainDestination.InviteMember
        destination?.hasRoute(ProjectHistoryRoute::class) == true -> MainDestination.ProjectHistory
        destination?.hasRoute(ProjectReportsRoute::class) == true -> MainDestination.ProjectReports
        destination?.hasRoute(CreateStageRoute::class) == true -> MainDestination.CreateStage
        destination?.hasRoute(StageDetailRoute::class) == true -> MainDestination.StageDetail
        destination?.hasRoute(DailyLogRoute::class) == true -> MainDestination.DailyLog
        destination?.hasRoute(EntrySummaryRoute::class) == true -> MainDestination.EntrySummary
        destination?.hasRoute(PurchaseLineFormRoute::class) == true -> MainDestination.PurchaseLineForm
        destination?.hasRoute(ConsumptionLineFormRoute::class) == true -> MainDestination.ConsumptionLineForm
        destination?.hasRoute(ReportEntryRoute::class) == true -> MainDestination.ReportEntry
        destination?.hasRoute(BillingRoute::class) == true -> MainDestination.Billing
        else -> MainDestination.Projects
    }
    val currentTab = when (current) {
        MainDestination.Projects -> MainTab.Projects
        MainDestination.Administration -> MainTab.Administration
        MainDestination.Settings -> MainTab.Settings
        else -> null
    }

    var detailTitle by remember { mutableStateOf<String?>(null) }
    var stageTitle by remember { mutableStateOf<String?>(null) }
    var logTitle by remember { mutableStateOf<String?>(null) }
    var formTitle by remember { mutableStateOf<String?>(null) }
    // History / report sort lives here, not in the screen body: it belongs in
    // the TopAppBar (Material 3 — a global filter, always reachable while scrolling).
    var historySort by remember { mutableStateOf(HistorySort.NEWEST_FIRST) }
    var reportSort by remember { mutableStateOf(ReportSort.NEWEST_FIRST) }
    var statsGranularity by remember { mutableStateOf(Granularity.MONTH) }
    val formDestinations = setOf(
        MainDestination.EntrySummary, MainDestination.PurchaseLineForm, MainDestination.ConsumptionLineForm,
    )
    LaunchedEffect(current) {
        if (current != MainDestination.ProjectDetail) detailTitle = null
        if (current != MainDestination.StageDetail) stageTitle = null
        if (current != MainDestination.DailyLog) logTitle = null
        if (current !in formDestinations) formTitle = null
        if (current != MainDestination.ProjectHistory) historySort = HistorySort.NEWEST_FIRST
        if (current != MainDestination.ProjectReports) reportSort = ReportSort.NEWEST_FIRST
        if (current != MainDestination.Administration) statsGranularity = Granularity.MONTH
    }

    Scaffold(
        topBar = {
            when (current) {
                MainDestination.CreateProject -> DetailTopBar(
                    title = stringResource(Res.string.create_project_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.ProjectDetail -> DetailTopBar(
                    title = detailTitle ?: stringResource(Res.string.detail_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.EditProject -> DetailTopBar(
                    title = stringResource(Res.string.edit_project_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.InviteMember -> DetailTopBar(
                    title = stringResource(Res.string.invite_member_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.ProjectHistory -> DetailTopBar(
                    title = stringResource(Res.string.history_title),
                    onBack = { navController.popBackStack() },
                    actions = { HistorySortControl(current = historySort, onSelect = { historySort = it }) },
                )
                MainDestination.ProjectReports -> DetailTopBar(
                    title = stringResource(Res.string.reports_title),
                    onBack = { navController.popBackStack() },
                    actions = { ReportSortControl(current = reportSort, onSelect = { reportSort = it }) },
                )
                MainDestination.CreateStage -> DetailTopBar(
                    title = stringResource(Res.string.create_stage_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.StageDetail -> DetailTopBar(
                    title = stageTitle ?: stringResource(Res.string.stage_detail_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.DailyLog -> DetailTopBar(
                    title = logTitle ?: stringResource(Res.string.daily_log_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.EntrySummary, MainDestination.PurchaseLineForm, MainDestination.ConsumptionLineForm -> DetailTopBar(
                    title = formTitle.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.ReportEntry -> DetailTopBar(
                    title = stringResource(Res.string.report_entry_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.Billing -> DetailTopBar(
                    title = stringResource(Res.string.billing_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.AdminUsers -> DetailTopBar(
                    title = stringResource(Res.string.admin_users_title),
                    onBack = { navController.popBackStack() },
                )
                MainDestination.AdminCreateUser -> DetailTopBar(
                    title = stringResource(Res.string.admin_create_user_title),
                    onBack = { navController.popBackStack() },
                )
                else -> AppTopBar(
                    userName = account.userName,
                    email = account.email,
                    plan = account.plan,
                    onSubscription = { navController.navigate(BillingRoute()) },
                    onLogout = viewModel::logout,
                    leadingActions = {
                        if (current == MainDestination.Projects) {
                            val sortHolder = koinInject<ProjectSortHolder>()
                            val sort by sortHolder.sort.collectAsStateWithLifecycle()
                            ProjectSortControl(current = sort, onSelect = sortHolder::set)
                        }
                        if (current == MainDestination.Administration) {
                            AdminStatsGranularityControl(current = statsGranularity, onSelect = { statsGranularity = it })
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                AppBottomBar(
                    current = currentTab,
                    tabs = tabs,
                    onSelect = { tab ->
                        navController.navigate(tab.route()) {
                            popUpTo(startDestination) { inclusive = false; saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (currentTab == MainTab.Projects) {
                FloatingActionButton(onClick = { navController.navigate(CreateProjectRoute) }) {
                    Icon(AddIcon, contentDescription = stringResource(Res.string.projects_new))
                }
            }
            if (current == MainDestination.AdminUsers) {
                FloatingActionButton(onClick = { navController.navigate(AdminCreateUserRoute) }) {
                    Icon(AddIcon, contentDescription = stringResource(Res.string.admin_users_create))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable<ProjectsRoute> {
                ProjectsScreen(onProjectClick = { localId -> navController.navigate(ProjectDetailRoute(localId)) })
            }
            composable<AdminStatsRoute> {
                AdminStatsScreen(
                    granularity = statsGranularity,
                    onManageUsers = { navController.navigate(AdminUsersRoute) },
                )
            }
            composable<AdminUsersRoute> {
                AdminUsersScreen()
            }
            composable<AdminCreateUserRoute> {
                AdminCreateUserScreen(onCreated = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<BillingRoute> { entry ->
                BillingScreen(notice = BillingNotice.fromArg(entry.toRoute<BillingRoute>().notice))
            }
            composable<CreateProjectRoute> {
                CreateProjectScreen(
                    onCreated = {
                        navController.popBackStack(ProjectsRoute, inclusive = false)
                    },
                )
            }
            composable<ProjectDetailRoute> { entry ->
                ProjectDetailScreen(
                    projectLocalId = entry.toRoute<ProjectDetailRoute>().projectLocalId,
                    onProjectNameResolved = { detailTitle = it },
                    onAddStage = { projectLocalId -> navController.navigate(CreateStageRoute(projectLocalId)) },
                    onStageClick = { stageLocalId -> navController.navigate(StageDetailRoute(stageLocalId)) },
                    onEditProject = { projectLocalId -> navController.navigate(EditProjectRoute(projectLocalId)) },
                    onInviteMember = { projectLocalId -> navController.navigate(InviteMemberRoute(projectLocalId)) },
                    onOpenHistory = { projectLocalId -> navController.navigate(ProjectHistoryRoute(projectLocalId)) },
                    onOpenReports = { projectLocalId -> navController.navigate(ProjectReportsRoute(projectLocalId)) },
                    onProjectDeleted = { navController.popBackStack(ProjectsRoute, inclusive = false) },
                )
            }
            composable<InviteMemberRoute> { entry ->
                InviteMemberScreen(
                    projectLocalId = entry.toRoute<InviteMemberRoute>().projectLocalId,
                    onInvited = { navController.popBackStack() },
                )
            }
            composable<ProjectHistoryRoute> { entry ->
                ProjectHistoryScreen(
                    projectLocalId = entry.toRoute<ProjectHistoryRoute>().projectLocalId,
                    sort = historySort,
                )
            }
            composable<ProjectReportsRoute> { entry ->
                ProjectReportsScreen(
                    projectLocalId = entry.toRoute<ProjectReportsRoute>().projectLocalId,
                    sort = reportSort,
                )
            }
            composable<EditProjectRoute> { entry ->
                EditProjectScreen(
                    projectLocalId = entry.toRoute<EditProjectRoute>().projectLocalId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<CreateStageRoute> { entry ->
                CreateStageScreen(
                    projectLocalId = entry.toRoute<CreateStageRoute>().projectLocalId,
                    onCreated = { navController.popBackStack() },
                )
            }
            composable<StageDetailRoute> { entry ->
                StageDetailScreen(
                    stageLocalId = entry.toRoute<StageDetailRoute>().stageLocalId,
                    onStageNameResolved = { stageTitle = it },
                    onOpenLog = { dailyLogLocalId -> navController.navigate(DailyLogRoute(dailyLogLocalId)) },
                )
            }
            composable<DailyLogRoute> { entry ->
                DailyLogScreen(
                    dailyLogLocalId = entry.toRoute<DailyLogRoute>().dailyLogLocalId,
                    onDateResolved = { logTitle = it },
                    onEditEntry = { entryLocalId -> navController.navigate(EntrySummaryRoute(entryLocalId)) },
                    onAddPurchaseLine = { entryLocalId, projectLocalId, currency ->
                        navController.navigate(PurchaseLineFormRoute(entryLocalId, projectLocalId, null, currency))
                    },
                    onEditPurchaseLine = { entryLocalId, projectLocalId, lineLocalId, currency ->
                        navController.navigate(PurchaseLineFormRoute(entryLocalId, projectLocalId, lineLocalId, currency))
                    },
                    onAddConsumptionLine = { entryLocalId, projectLocalId ->
                        navController.navigate(ConsumptionLineFormRoute(entryLocalId, projectLocalId))
                    },
                    onEditConsumptionLine = { entryLocalId, projectLocalId, lineLocalId ->
                        navController.navigate(ConsumptionLineFormRoute(entryLocalId, projectLocalId, lineLocalId))
                    },
                    onReportEntry = { entryLocalId -> navController.navigate(ReportEntryRoute(entryLocalId)) },
                )
            }
            composable<EntrySummaryRoute> { entry ->
                EntrySummaryScreen(
                    entryLocalId = entry.toRoute<EntrySummaryRoute>().entryLocalId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTitleResolved = { formTitle = it },
                )
            }
            composable<ReportEntryRoute> { entry ->
                ReportEntryScreen(
                    entryLocalId = entry.toRoute<ReportEntryRoute>().entryLocalId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<PurchaseLineFormRoute> { entry ->
                val route = entry.toRoute<PurchaseLineFormRoute>()
                PurchaseLineFormScreen(
                    entryLocalId = route.entryLocalId,
                    projectLocalId = route.projectLocalId,
                    lineLocalId = route.lineLocalId,
                    currency = route.currency,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTitleResolved = { formTitle = it },
                )
            }
            composable<ConsumptionLineFormRoute> { entry ->
                val route = entry.toRoute<ConsumptionLineFormRoute>()
                ConsumptionLineFormScreen(
                    entryLocalId = route.entryLocalId,
                    projectLocalId = route.projectLocalId,
                    lineLocalId = route.lineLocalId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTitleResolved = { formTitle = it },
                )
            }
        }
    }
}
