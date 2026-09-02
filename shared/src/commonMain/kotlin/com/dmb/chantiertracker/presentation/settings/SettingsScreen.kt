package com.dmb.chantiertracker.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.i18n.LocalAppLocale
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.language_english
import com.dmb.chantiertracker.resources.language_french
import com.dmb.chantiertracker.resources.settings_language
import com.dmb.chantiertracker.resources.settings_theme
import com.dmb.chantiertracker.resources.settings_theme_system
import com.dmb.chantiertracker.resources.settings_version
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val activeLocale = LocalAppLocale.current
    val languageLabel = if (activeLocale.lowercase().startsWith("fr")) {
        stringResource(Res.string.language_french)
    } else {
        stringResource(Res.string.language_english)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SettingsRow(stringResource(Res.string.settings_language), languageLabel)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsRow(stringResource(Res.string.settings_theme), stringResource(Res.string.settings_theme_system))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsRow(stringResource(Res.string.settings_version), viewModel.appVersion)
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
