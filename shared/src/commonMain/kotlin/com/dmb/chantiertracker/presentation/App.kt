package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.theme.AppTheme
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    AppEnvironment {
        AppTheme {
            AppChrome(showStagingBanner = koinInject<BuildInfo>().isStaging) {
                RootNavHost()
            }
        }
    }
}
