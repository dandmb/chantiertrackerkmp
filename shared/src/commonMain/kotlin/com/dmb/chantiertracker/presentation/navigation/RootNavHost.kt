package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.plans.PlanSelectionScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterScreen
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordScreen
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailScreen
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.main.MainScreen
import com.dmb.chantiertracker.presentation.onboarding.OnboardingScreen
import com.dmb.chantiertracker.presentation.splash.SplashScreen
import org.koin.compose.viewmodel.koinViewModel

private enum class AuthStartPoint { Onboarding, Welcome, Login }

@Composable
fun RootNavHost(viewModel: RootViewModel = koinViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val hasLoggedInBefore by viewModel.hasLoggedInBefore.collectAsStateWithLifecycle()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()

    when {
        authState is AuthState.Unknown || hasLoggedInBefore == null || hasSeenOnboarding == null -> SplashScreen()
        authState is AuthState.Authenticated -> MainScreen()
        else -> AuthNavHost(
            startPoint = when {
                hasLoggedInBefore == true -> AuthStartPoint.Login
                hasSeenOnboarding == true -> AuthStartPoint.Welcome
                else -> AuthStartPoint.Onboarding
            },
            onOnboardingFinished = viewModel::markOnboardingSeen,
        )
    }
}

@Composable
private fun AuthNavHost(startPoint: AuthStartPoint, onOnboardingFinished: () -> Unit) {
    val navController = rememberNavController()
    val neverLoggedIn = startPoint != AuthStartPoint.Login

    NavHost(
        navController = navController,
        startDestination = when (startPoint) {
            AuthStartPoint.Onboarding -> OnboardingRoute
            AuthStartPoint.Welcome -> WelcomeRoute
            AuthStartPoint.Login -> LoginRoute()
        },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinish = {
                    onOnboardingFinished()
                    navController.navigate(WelcomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCreateAccount = { navController.navigate(RegisterRoute()) },
                onSignIn = { navController.navigate(LoginRoute()) },
                onDiscoverPlans = { navController.navigate(PlanSelectionRoute) },
            )
        }
        composable<PlanSelectionRoute> {
            PlanSelectionScreen(
                onSelectPlan = { plan, cycle ->
                    navController.navigate(RegisterRoute(checkoutPlan = plan.name, checkoutCycle = cycle.name))
                },
                onContinueFree = { navController.navigate(RegisterRoute()) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<LoginRoute> { entry ->
            val route = entry.toRoute<LoginRoute>()
            LoginScreen(
                onNavigateToRegister = { navController.navigate(RegisterRoute()) },
                onNavigateToForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                prefilledEmail = route.prefilledEmail,
                notice = LoginNotice.fromArg(route.notice),
                checkoutPlan = route.checkoutPlan,
                checkoutCycle = route.checkoutCycle,
            )
        }
        composable<RegisterRoute> { entry ->
            val route = entry.toRoute<RegisterRoute>()
            RegisterScreen(
                onRegistered = { email ->
                    navController.navigate(VerifyEmailRoute(email, route.checkoutPlan, route.checkoutCycle))
                },
                onBackToLogin = { navController.backToLogin(neverLoggedIn) },
                onBack = { navController.popBackStack() },
            )
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
        composable<ForgotPasswordRoute> {
            ForgotPasswordScreen(
                onCodeSent = { email -> navController.navigate(ResetPasswordRoute(email)) },
                onBack = { navController.popBackStack() },
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
            popUpTo(WelcomeRoute) { inclusive = false }
        } else {
            popUpTo(0) { inclusive = true }
        }
        launchSingleTop = true
    }
}
