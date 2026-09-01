package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.theme.AppTheme

@Composable
@Preview
fun App() {
    AppTheme {
        RootNavHost()
    }
}
