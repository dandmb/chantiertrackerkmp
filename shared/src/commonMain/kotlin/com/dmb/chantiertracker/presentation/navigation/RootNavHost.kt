package com.dmb.chantiertracker.presentation.navigation

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.mp.KoinPlatformTools
import androidx.navigation.NavDestination.Companion.hasRoute
import com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    when {
        authState is AuthState.Unknown || hasLoggedInBefore == null || hasSeenOnboarding == null -> SplashScreen()
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
    SignedOutRootBackExitGuard(enabled = backStackEntry != null && navController.previousBackStackEntry == null)
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
    LaunchedEffect(pendingInvitation, backStackEntry) {
        val entry = backStackEntry ?: return@LaunchedEffect
        if (pendingInvitation == null || entry.destination.hasRoute<LoginRoute>()) return@LaunchedEffect
        if (entry.destination.hasRoute<OnboardingRoute>() || entry.destination.hasRoute<WelcomeRoute>()) {
            navController.navigate(LoginRoute(notice = LoginNotice.InvitationPending.toArg())) { launchSingleTop = true }
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
                notice = LoginNotice.fromArg(route.notice)
                    ?: LoginNotice.InvitationPending.takeIf { pendingInvitation != null },
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
