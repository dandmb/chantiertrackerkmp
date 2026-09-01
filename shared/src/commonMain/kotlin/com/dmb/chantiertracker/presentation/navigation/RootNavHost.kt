package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmb.chantiertracker.presentation.hello.HelloScreen

@Composable
fun RootNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HelloRoute,
    ) {
        composable<HelloRoute> {
            HelloScreen()
        }
    }
}
