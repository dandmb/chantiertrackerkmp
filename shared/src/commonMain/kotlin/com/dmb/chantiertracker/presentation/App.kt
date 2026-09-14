package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.settings.AppSettings
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    val settings = koinInject<AppSettings>()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // The persisted language is applied by AppSettings before this composes;
    // AppEnvironment re-keys the tree when the user changes it (via customAppLocale).
    AppEnvironment {
        AppTheme(darkTheme = darkTheme) {
            AppChrome(showStagingBanner = koinInject<BuildInfo>().isStaging) {
                RootNavHost()
            }
        }
    }
}
