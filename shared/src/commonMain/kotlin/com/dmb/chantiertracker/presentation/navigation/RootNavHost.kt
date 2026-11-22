package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.material3.MaterialTheme
import com.dmb.chantiertracker.presentation.theme.SystemBarIcons
import com.dmb.chantiertracker.presentation.theme.systemBarIconsAreLight
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.mp.KoinPlatformTools
import androidx.navigation.NavDestination.Companion.hasRoute
import com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.InvitationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dmb.chantiertracker.data.session.SessionNotice
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.auth.changepassword.ChangePasswordScreen
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.plans.PlanSelectionScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterScreen
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordScreen
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailScreen
import com.dmb.chantiertracker.presentation.auth.verify.VerifyAccountFromLoginScreen
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.billing.paidPlansAreOffered
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.legal.LegalDocumentViewModel
import com.dmb.chantiertracker.presentation.legal.StandaloneLegalDocumentScreen
import com.dmb.chantiertracker.presentation.main.MainScreen
import com.dmb.chantiertracker.presentation.onboarding.OnboardingScreen
import com.dmb.chantiertracker.presentation.splash.SplashScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class AuthStartPoint { Onboarding, Welcome, Login }

@Composable
fun RootNavHost(
    viewModel: RootViewModel = koinViewModel(),
    legalDocumentViewModel: @Composable () -> LegalDocumentViewModel = { koinViewModel() },
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val hasLoggedInBefore by viewModel.hasLoggedInBefore.collectAsStateWithLifecycle()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()
    val sessionNotice by viewModel.sessionNotice.collectAsStateWithLifecycle()

    // Read here, not inside MainViewModel's own (async-populated) state: the
    // Projects↔Administration tab swap (ADR-52) decides MainScreen's NavHost
    // startDestination, which Compose Navigation only ever evaluates once —
    // it must already be correct on the very first composition, and
    // AuthState.Authenticated already carries it synchronously.
    val authenticatedUser = (authState as? AuthState.Authenticated)?.user
    val mustChangePasswordEmail = (authState as? AuthState.MustChangePassword)?.email

    val showingSplash = authState is AuthState.Unknown || hasLoggedInBefore == null || hasSeenOnboarding == null
    SystemBarIcons(light = systemBarIconsAreLight(showingSplash, MaterialTheme.colorScheme.background))

    when {
        showingSplash -> SplashScreen()
        mustChangePasswordEmail != null -> ChangePasswordScreen(email = mustChangePasswordEmail)
        authenticatedUser != null -> MainScreen(globalRole = authenticatedUser.globalRole)
        else -> AuthNavHost(
            startPoint = when {
                sessionNotice == SessionNotice.AccountDeleted -> AuthStartPoint.Login
                hasLoggedInBefore == true -> AuthStartPoint.Login
                hasSeenOnboarding == true -> AuthStartPoint.Welcome
                else -> AuthStartPoint.Onboarding
            },
            onOnboardingFinished = viewModel::markOnboardingSeen,
            onOnboardingShown = viewModel::recordOnboardingShown,
            startNotice = if (sessionNotice == SessionNotice.AccountDeleted) LoginNotice.AccountDeleted else null,
            legalDocumentViewModel = legalDocumentViewModel,
        )
    }
}

@Composable
private fun AuthNavHost(
    startPoint: AuthStartPoint,
    onOnboardingFinished: () -> Unit,
    onOnboardingShown: () -> Unit,
    startNotice: LoginNotice? = null,
    legalDocumentViewModel: @Composable () -> LegalDocumentViewModel,
) {
    val navController = rememberNavController()
    val transitions = rememberNavigationTransitions()
    val backStackEntry by navController.currentBackStackEntryAsState()
    SignedOutRootBackExit(navController)
    val neverLoggedIn = startPoint != AuthStartPoint.Login
    val startDestination: Any = remember {
        when (startPoint) {
            AuthStartPoint.Onboarding -> OnboardingRoute
            AuthStartPoint.Welcome -> WelcomeRoute()
            AuthStartPoint.Login -> LoginRoute(notice = startNotice?.toArg())
        }
    }
    val paidPlansOffered = paidPlansAreOffered()
    val openLegalDocument: (LegalDocument) -> Unit = { navController.navigate(LegalDocumentRoute(it.slug)) }
    val invitationLinks = remember { KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<InvitationDeepLinkDispatcher>() }
    LaunchedEffect(invitationLinks) { invitationLinks?.reopenHeld() }
    val pendingInvitation by (invitationLinks?.pending ?: NoPendingInvitation).collectAsStateWithLifecycle()
    val invitationRepository = remember { KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<InvitationRepository>() }
    var accountCheck by remember { mutableStateOf<InvitationAccountCheck?>(null) }
    LaunchedEffect(pendingInvitation) {
        val token = pendingInvitation ?: return@LaunchedEffect
        if (accountCheck?.token == token) return@LaunchedEffect
        val invitedAccount = invitationRepository?.invitedAccount(token) ?: InvitedAccount.Unknown
        if (invitedAccount == InvitedAccount.Settled) {
            invitationLinks?.release(token)
            return@LaunchedEffect
        }
        accountCheck = InvitationAccountCheck(token, accountMissing = invitedAccount == InvitedAccount.Missing)
    }
    val dismissInvitation: () -> Unit = { invitationLinks?.forget() }
    val linkOpening by (invitationLinks?.openedByLink ?: NoPendingInvitation).collectAsStateWithLifecycle()
    LaunchedEffect(pendingInvitation, backStackEntry, accountCheck, linkOpening) {
        val entry = backStackEntry ?: return@LaunchedEffect
        val token = pendingInvitation ?: return@LaunchedEffect
        val check = accountCheck?.takeIf { it.token == token } ?: return@LaunchedEffect
        val destination = entry.destination
        val onStartScreen = destination.hasRoute<OnboardingRoute>() || destination.hasRoute<WelcomeRoute>()
        val openedByLink = invitationLinks?.claimLinkOpening(token) == true
        if (check.accountMissing && openedByLink) {
            if (onStartScreen || destination.hasRoute<LoginRoute>()) {
                navController.navigate(RegisterRoute()) { launchSingleTop = true }
            }
        } else if (onStartScreen) {
            navController.navigate(LoginRoute(notice = LoginNotice.InvitationPending.toArg())) {
                popUpTo(destination.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        enterTransition = { transitions.enter },
        exitTransition = { transitions.exit },
        popEnterTransition = { transitions.popEnter },
        popExitTransition = { transitions.popExit },
        startDestination = startDestination,
    ) {
        composable<OnboardingRoute> {
            LaunchedEffect(Unit) { onOnboardingShown() }
            OnboardingScreen(
                onFinish = {
                    onOnboardingFinished()
                    navController.navigate(WelcomeRoute(showPresentation = true)) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<WelcomeRoute> { entry ->
            WelcomeScreen(
                presentationOpenAtStart = entry.toRoute<WelcomeRoute>().showPresentation,
                onCreateAccount = { navController.navigate(RegisterRoute()) },
                onSignIn = { navController.navigate(LoginRoute()) },
                onDiscoverPlans = if (paidPlansOffered) {
                    { navController.navigate(PlanSelectionRoute) }
                } else {
                    null
                },
                onOpenLegalDocument = openLegalDocument,
            )
        }
        composable<PlanSelectionRoute> {
            // The switch can close (or a refresh can fail) while this screen is
            // on the back stack — it then leaves rather than keep showing prices.
            if (paidPlansOffered) {
                PlanSelectionScreen(
                    onSelectPlan = { plan, cycle ->
                        navController.navigate(RegisterRoute(checkoutPlan = plan.name, checkoutCycle = cycle.name))
                    },
                    onContinueFree = { navController.navigate(RegisterRoute()) },
                    onBack = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack(PlanSelectionRoute, inclusive = true) }
            }
        }
        composable<LoginRoute> { entry ->
            val route = entry.toRoute<LoginRoute>()
            LoginScreen(
                onNavigateToRegister = { navController.navigate(RegisterRoute()) },
                onNavigateToForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email)) },
                onOpenLegalDocument = openLegalDocument,
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onVerificationRequired = { navController.navigate(LoginVerificationRoute(route.checkoutPlan, route.checkoutCycle)) },
                onValidateAccount = { typed -> navController.navigate(ValidateAccountRoute(typed.ifBlank { null })) },
                prefilledEmail = route.prefilledEmail,
                notice = LoginNotice.fromArg(route.notice)?.takeUnless { it == LoginNotice.InvitationPending }
                    ?: LoginNotice.InvitationPending.takeIf { pendingInvitation != null },
                onDismissInvitation = dismissInvitation,
                checkoutPlan = route.checkoutPlan,
                checkoutCycle = route.checkoutCycle,
            )
        }
        composable<ValidateAccountRoute> { entry ->
            VerifyAccountFromLoginScreen(
                prefilledEmail = entry.toRoute<ValidateAccountRoute>().email,
                onVerified = { email -> navController.backToLogin(neverLoggedIn, email, LoginNotice.AccountActivated) },
                onBackToLogin = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<RegisterRoute> { entry ->
            val route = entry.toRoute<RegisterRoute>()
            RegisterScreen(
                onRegistered = { email ->
                    navController.navigate(VerifyEmailRoute(email, route.checkoutPlan, route.checkoutCycle))
                },
                onBackToLogin = { navController.backToLogin(neverLoggedIn) },
                onOpenLegalDocument = openLegalDocument,
                onBack = { navController.popBackStack() },
                invitationPending = pendingInvitation != null,
                onDismissInvitation = dismissInvitation,
            )
        }
        composable<LoginVerificationRoute> { entry ->
            val route = entry.toRoute<LoginVerificationRoute>()
            val authRepository = koinInject<AuthRepository>()
            val email = remember { authRepository.pendingVerificationEmail() }
            if (email != null) {
                VerifyEmailScreen(
                    email = email,
                    onVerified = {
                        navController.backToLogin(
                            neverLoggedIn,
                            email,
                            LoginNotice.AccountActivated,
                            route.checkoutPlan,
                            route.checkoutCycle,
                        )
                    },
                    onCancelVerification = { navController.backToLogin(neverLoggedIn) },
                    onBack = { navController.popBackStack() },
                    unverifiedLogin = true,
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
        composable<VerifyEmailRoute> { entry ->
            val route = entry.toRoute<VerifyEmailRoute>()
            VerifyEmailScreen(
                email = route.email,
                onVerified = {
                    navController.backToLogin(
                        neverLoggedIn,
                        route.email,
                        LoginNotice.AccountActivated,
                        route.checkoutPlan,
                        route.checkoutCycle,
                    )
                },
                onCancelVerification = { navController.backToLogin(neverLoggedIn) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<LegalDocumentRoute> { entry ->
            LegalDocument.fromSlug(entry.toRoute<LegalDocumentRoute>().document)?.let { document ->
                StandaloneLegalDocumentScreen(
                    document,
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { navController.navigate(LegalDocumentRoute(it.slug)) },
                    viewModel = legalDocumentViewModel(),
                    onOpenForgotPassword = { navController.navigate(ForgotPasswordRoute()) },
                )
            }
        }
        composable<ForgotPasswordRoute> { entry ->
            ForgotPasswordScreen(
                onCodeSent = { email -> navController.navigate(ResetPasswordRoute(email)) },
                onBack = { navController.popBackStack() },
                prefilledEmail = entry.toRoute<ForgotPasswordRoute>().email,
            )
        }
        composable<ResetPasswordRoute> { entry ->
            val route = entry.toRoute<ResetPasswordRoute>()
            ResetPasswordScreen(
                email = route.email,
                onReset = { navController.backToLogin(neverLoggedIn, route.email, LoginNotice.PasswordReset) },
                onBackToLogin = { navController.backToLogin(neverLoggedIn) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavController.backToLogin(
    neverLoggedIn: Boolean,
    prefilledEmail: String? = null,
    notice: LoginNotice? = null,
    checkoutPlan: String? = null,
    checkoutCycle: String? = null,
) {
    navigate(LoginRoute(prefilledEmail, notice?.toArg(), checkoutPlan, checkoutCycle)) {
        if (neverLoggedIn) {
            popUpTo<WelcomeRoute> { inclusive = false }
        } else {
            popUpTo(0) { inclusive = true }
        }
        launchSingleTop = true
    }
}

private val NoPendingInvitation: StateFlow<String?> = MutableStateFlow(null)

private class InvitationAccountCheck(val token: String, val accountMissing: Boolean)

private enum class InvitedAccount { Missing, Exists, Settled, Unknown }

private const val ACCOUNT_CHECK_TIMEOUT_MILLIS = 3_000L

private suspend fun InvitationRepository.invitedAccount(token: String): InvitedAccount =
    try {
        withTimeoutOrNull(ACCOUNT_CHECK_TIMEOUT_MILLIS) {
            val preview = getInvitationPreview(token)
            when {
                preview.status != InvitationStatus.PENDING -> InvitedAccount.Settled
                preview.accountExists -> InvitedAccount.Exists
                else -> InvitedAccount.Missing
            }
        } ?: InvitedAccount.Unknown
    } catch (e: CancellationException) {
        throw e
    } catch (e: DomainException.NotFound) {
        InvitedAccount.Settled
    } catch (e: Exception) {
        InvitedAccount.Unknown
    }
