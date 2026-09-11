package com.dmb.chantiertracker.presentation.billing

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override suspend fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
