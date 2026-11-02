package com.dmb.chantiertracker.presentation.help

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidSupportMailer(private val context: Context) : SupportMailer {
    override suspend fun compose(address: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
