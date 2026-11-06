package com.dmb.chantiertracker.presentation.navigation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }
}

@Composable
actual fun rememberExitApp(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            var current: android.content.Context? = context
            while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
            (current as? android.app.Activity)?.finish()
        }
    }
}
