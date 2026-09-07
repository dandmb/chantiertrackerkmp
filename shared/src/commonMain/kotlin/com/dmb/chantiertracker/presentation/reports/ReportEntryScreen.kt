package com.dmb.chantiertracker.presentation.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_close
import com.dmb.chantiertracker.resources.report_message_label
import com.dmb.chantiertracker.resources.report_message_placeholder
import com.dmb.chantiertracker.resources.report_sent
import com.dmb.chantiertracker.resources.report_submit
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportEntryScreen(
    entryLocalId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportEntryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(entryLocalId) { viewModel.load(entryLocalId) }

    Box(modifier.fillMaxSize()) {
        if (state.sent) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    CheckIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(Res.string.report_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                AuthPrimaryButton(text = stringResource(Res.string.action_close), onClick = onDone)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.message,
                    onValueChange = viewModel::onMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.report_message_label)) },
                    placeholder = { Text(stringResource(Res.string.report_message_placeholder)) },
                    minLines = 4,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it.localizedText()) } },
                    enabled = !state.isSubmitting,
                )
                AuthPrimaryButton(
                    text = stringResource(Res.string.report_submit),
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                    enabled = state.canSend,
                )
            }
        }
    }
}
