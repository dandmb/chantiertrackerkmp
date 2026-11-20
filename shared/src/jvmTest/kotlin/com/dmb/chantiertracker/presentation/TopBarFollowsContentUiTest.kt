package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dmb.chantiertracker.presentation.navigation.NavigationTransitions
import com.dmb.chantiertracker.presentation.navigation.TopBarFollowingContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TopBarFollowsContentUiTest {

    private fun colorOf(route: String?) = if (route == "detail") Color.Blue else Color.Red

    private fun name(color: Color) = when (color) {
        Color.Red -> "liste"
        Color.Blue -> "détail"
        else -> "vide"
    }

    @Test
    fun with_animations_removed_the_top_bar_changes_on_the_same_frame_as_the_content() = runComposeUiTest {
        val transitions = NavigationTransitions.of(reduceMotion = true)
        lateinit var navController: NavHostController
        mainClock.autoAdvance = false
        setContent {
            navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            Column(Modifier.size(200.dp, 120.dp).background(Color.White)) {
                Box(Modifier.fillMaxWidth().height(20.dp)) {
                    TopBarFollowingContent(entry?.destination?.route, transitions) { route ->
                        Box(Modifier.fillMaxSize().background(colorOf(route)))
                    }
                }
                NavHost(
                    navController, startDestination = "list",
                    enterTransition = { transitions.enter }, exitTransition = { transitions.exit },
                    popEnterTransition = { transitions.popEnter }, popExitTransition = { transitions.popExit },
                ) {
                    composable("list") { Box(Modifier.fillMaxSize().background(Color.Red)) }
                    composable("detail") { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                }
            }
        }
        repeat(5) { mainClock.advanceTimeByFrame() }

        val mismatches = mutableListOf<String>()
        for (move in listOf("aller", "retour")) {
            runOnUiThread { if (move == "aller") navController.navigate("detail") else navController.popBackStack() }
            repeat(8) { frame ->
                mainClock.advanceTimeByFrame()
                val pixels = onRoot().captureToImage().toPixelMap()
                val bar = name(pixels[100, 10])
                val content = name(pixels[100, 80])
                if (content != "vide" && bar != content) mismatches += "$move image $frame : barre $bar, contenu $content"
            }
        }
        assertEquals(emptyList(), mismatches)
    }
}
