package com.dmb.chantiertracker.presentation.account

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.ProfileLanguage
import com.dmb.chantiertracker.presentation.AppOutlinedTextField
import com.dmb.chantiertracker.presentation.DetailInfoRow
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.profile_email_help
import com.dmb.chantiertracker.resources.profile_email_label
import com.dmb.chantiertracker.resources.profile_language_en
import com.dmb.chantiertracker.resources.profile_language_fr
import com.dmb.chantiertracker.resources.profile_language_help
import com.dmb.chantiertracker.resources.profile_language_label
import com.dmb.chantiertracker.resources.profile_name_label
import com.dmb.chantiertracker.resources.profile_phone_help
import com.dmb.chantiertracker.resources.profile_phone_label
import com.dmb.chantiertracker.resources.profile_save
import com.dmb.chantiertracker.resources.profile_section_identity
import com.dmb.chantiertracker.resources.profile_section_preferences
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    ResponsiveContent(modifier, maxContentWidth = 560.dp) {
        Box(Modifier.fillMaxSize()) {
            if (!state.loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                ProfileForm(state, viewModel)
            }
        }
    }
}

@Composable
private fun ProfileForm(state: ProfileUiState, viewModel: ProfileViewModel) {
    val locked = state.isSubmitting
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.formError?.let { ErrorBanner(it.localizedText()) }

        DetailSection(stringResource(Res.string.profile_section_identity)) {
            DetailInfoRow(stringResource(Res.string.profile_email_label), state.email)
            Text(
                text = stringResource(Res.string.profile_email_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppOutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.profile_name_label)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(stringResource(it)) } },
                enabled = !locked,
            )
            AppOutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.profile_phone_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = state.phoneError != null,
                supportingText = { Text(stringResource(state.phoneError ?: Res.string.profile_phone_help)) },
                enabled = !locked,
            )
        }

        DetailSectionDivider()

        DetailSection(stringResource(Res.string.profile_section_preferences)) {
            Text(stringResource(Res.string.profile_language_label), style = MaterialTheme.typography.bodyMedium)
            LanguageChoice(selected = state.language, enabled = !locked, onSelect = viewModel::onLanguageChange)
            Text(
                text = stringResource(state.languageError ?: Res.string.profile_language_help),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.languageError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AuthPrimaryButton(
            text = stringResource(Res.string.profile_save),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
    }
}

@Composable
private fun LanguageChoice(selected: ProfileLanguage, enabled: Boolean, onSelect: (ProfileLanguage) -> Unit) {
    val options = ProfileLanguage.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, language ->
            SegmentedButton(
                selected = language == selected,
                onClick = { onSelect(language) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    text = stringResource(
                        when (language) {
                            ProfileLanguage.FRENCH -> Res.string.profile_language_fr
                            ProfileLanguage.ENGLISH -> Res.string.profile_language_en
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
