package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object DesktopReduceMotionSetting : ReduceMotionSetting {
    override fun isOn(): Boolean = false
    override fun watch(onChange: () -> Unit): () -> Unit = {}
}

@Composable
actual fun rememberReduceMotionSetting(): ReduceMotionSetting = DesktopReduceMotionSetting

@Composable
actual fun rememberExitApp(): () -> Unit = remember { {} }
