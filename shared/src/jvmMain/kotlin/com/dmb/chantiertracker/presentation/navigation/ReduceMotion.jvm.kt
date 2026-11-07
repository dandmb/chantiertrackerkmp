package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberReduceMotion(): Boolean = false

@Composable
actual fun rememberExitApp(): () -> Unit = remember { {} }
