package com.dmb.chantiertracker.presentation.billing

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override suspend fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            // FLAG_ACTIVITY_NEW_TASK: the browser gets its own task, never
            // merged into ours. FLAG_ACTIVITY_NO_HISTORY (ADR-51 point 4 bug
            // fix): that browser task is dropped from history the moment the
            // user navigates away from it — which is exactly what happens
            // when Stripe redirects back to chantiertracker://… and our
            // singleTask MainActivity is brought back to the front. Without
            // it, the browser task lingered forever; once our own Compose
            // back stack was exhausted, Android's "reveal the next
            // most-recently-used task" behavior surfaced that leftover
            // browser tab instead of exiting the app — confirmed on a real
            // device via `dumpsys activity recents` before this fix.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
        }
        context.startActivity(intent)
    }
}
