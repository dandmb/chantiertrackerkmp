package com.dmb.chantiertracker.presentation.navigation

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AndroidReduceMotionSetting(private val resolver: ContentResolver) : ReduceMotionSetting {
    private val scales = listOf(Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE)

    override fun isOn(): Boolean = scales.any { Settings.Global.getFloat(resolver, it, 1f) == 0f }

    override fun watch(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        scales.forEach { resolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer) }
        return { resolver.unregisterContentObserver(observer) }
    }
}

@Composable
actual fun rememberReduceMotionSetting(): ReduceMotionSetting {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { AndroidReduceMotionSetting(resolver) }
}

@Composable
actual fun rememberExitApp(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            var current: android.content.Context? = context
            while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
            (current as? android.app.Activity)?.finishAndRemoveTask()
        }
    }
}
