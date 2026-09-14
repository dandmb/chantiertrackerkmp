package com.dmb.chantiertracker.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmb.chantiertracker.presentation.navigation.BillingNotice
import com.dmb.chantiertracker.presentation.navigation.BillingRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectsRoute
import kotlin.test.Test
import kotlin.test.assertTrue

// ADR-51 point 4 bug fix — device-confirmed on a real phone: pressing back
// after a chantiertracker:// return landed on the leftover Stripe browser
// tab instead of leaving BillingScreen normally (see
// docs/walkthrough/retour-checkout-stripe.md for the full device
// investigation). One contributing cause, verifiable in isolation: the
// LaunchedEffect that reacts to the deep link used a plain
// navController.navigate(BillingRoute(...)) — landing here from
// BillingRoute itself ("Gérer mon abonnement" lives on that screen, the
// most common case) pushed a *second*, invisible BillingRoute entry on top
// of the existing one instead of replacing it, padding the back stack by
// one extra required press before really leaving the screen.
//
// A minimal NavHost fixture (not the full MainScreen/Koin) is enough to
// isolate this pure Navigation-Compose behavior — same reasoning as why
// MainScreen itself has never been unit-tested as a whole in this repo.
@OptIn(ExperimentalTestApi::class)
class BillingRouteDeepLinkNavigationTest {

    @Composable
    private fun Fixture(navController: NavHostController) {
        NavHost(navController = navController, startDestination = ProjectsRoute) {
            composable<ProjectsRoute> {}
            composable<BillingRoute> {}
        }
    }

    @Test
    fun the_bug_reproduced_a_plain_navigate_call_stacks_a_second_billing_route_entry() = runComposeUiTest {
        lateinit var navController: NavHostController
        setContent {
            navController = rememberNavController()
            Fixture(navController)
        }
        waitForIdle()

        // Already on BillingRoute — the state when "Gérer mon abonnement"
        // is tapped.
        navController.navigate(BillingRoute())
        waitForIdle()

        // The pre-fix deep-link handler's exact call: a plain navigate(),
        // no popUpTo/launchSingleTop.
        navController.navigate(BillingRoute(BillingNotice.CheckoutSucceeded.toArg()))
        waitForIdle()

        // One back press only pops the newly-pushed duplicate — still on
        // BillingRoute, not back to ProjectsRoute. This is the extra,
        // invisible press the device bug report actually needed.
        navController.popBackStack()
        waitForIdle()
        assertTrue(navController.currentDestination?.hasRoute(BillingRoute::class) == true)
    }

    @Test
    fun the_fix_popUpTo_and_launchSingleTop_replace_the_existing_entry_instead_of_stacking() = runComposeUiTest {
        lateinit var navController: NavHostController
        setContent {
            navController = rememberNavController()
            Fixture(navController)
        }
        waitForIdle()

        navController.navigate(BillingRoute())
        waitForIdle()

        // The fixed deep-link handler's exact call (MainScreen.kt).
        navController.navigate(BillingRoute(BillingNotice.CheckoutSucceeded.toArg())) {
            popUpTo(BillingRoute::class) { inclusive = true }
            launchSingleTop = true
        }
        waitForIdle()

        // A single back press now leaves BillingRoute entirely — exactly
        // one entry was ever on the stack, the duplicate never existed.
        navController.popBackStack()
        waitForIdle()
        assertTrue(navController.currentDestination?.hasRoute(ProjectsRoute::class) == true)
    }
}
