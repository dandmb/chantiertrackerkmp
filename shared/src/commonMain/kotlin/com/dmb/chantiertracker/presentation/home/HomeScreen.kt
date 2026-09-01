package com.dmb.chantiertracker.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.home_api
import com.dmb.chantiertracker.resources.home_greeting
import com.dmb.chantiertracker.resources.home_greeting_fallback
import com.dmb.chantiertracker.resources.home_logout
import com.dmb.chantiertracker.resources.home_platform
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (state.userName.isNotBlank()) {
                    stringResource(Res.string.home_greeting, state.userName)
                } else {
                    stringResource(Res.string.home_greeting_fallback)
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.email,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.home_platform, state.platformName),
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(Res.string.home_api, state.apiBaseUrl),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier.padding(top = 32.dp),
                enabled = !state.isLoggingOut,
            ) {
                Text(stringResource(Res.string.home_logout))
            }
        }
    }
}
