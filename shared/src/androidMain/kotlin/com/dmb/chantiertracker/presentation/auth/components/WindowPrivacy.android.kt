package com.dmb.chantiertracker.presentation.auth.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

private val holdsByActivity = WeakHashMap<Activity, WindowPrivacyHolds>()

@Composable
actual fun KeepWindowPrivate() {
    val activity = LocalContext.current.activityOrNull() ?: return
    DisposableEffect(activity) {
        val holds = holdsByActivity.getOrPut(activity) {
            WindowPrivacyHolds { private ->
                if (private) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        holds.acquire()
        onDispose { holds.release() }
    }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
