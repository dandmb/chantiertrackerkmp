package com.dmb.chantiertracker.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.DetailInfoRow
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.i18n.AppLanguage
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.settings_about
import com.dmb.chantiertracker.resources.settings_language
import com.dmb.chantiertracker.resources.settings_language_english
import com.dmb.chantiertracker.resources.settings_language_french
import com.dmb.chantiertracker.resources.settings_option_system
import com.dmb.chantiertracker.resources.settings_theme
import com.dmb.chantiertracker.resources.settings_theme_dark
import com.dmb.chantiertracker.resources.settings_theme_light
import com.dmb.chantiertracker.resources.settings_version
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DetailSection(stringResource(Res.string.settings_language)) {
            ChoiceRow(
                options = AppLanguage.entries,
                selected = language,
                label = ::languageLabel,
                onSelect = viewModel::onLanguageSelected,
            )
        }

        DetailSectionDivider()

        DetailSection(stringResource(Res.string.settings_theme)) {
            ChoiceRow(
                options = ThemeMode.entries,
                selected = themeMode,
                label = ::themeLabel,
                onSelect = viewModel::onThemeSelected,
            )
        }

        DetailSectionDivider()

        DetailSection(stringResource(Res.string.settings_about)) {
            DetailInfoRow(stringResource(Res.string.settings_version), viewModel.appVersion)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> StringResource,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    text = stringResource(label(option)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun languageLabel(language: AppLanguage): StringResource = when (language) {
    AppLanguage.System -> Res.string.settings_option_system
    AppLanguage.French -> Res.string.settings_language_french
    AppLanguage.English -> Res.string.settings_language_english
}

private fun themeLabel(mode: ThemeMode): StringResource = when (mode) {
    ThemeMode.System -> Res.string.settings_option_system
    ThemeMode.Light -> Res.string.settings_theme_light
    ThemeMode.Dark -> Res.string.settings_theme_dark
}
