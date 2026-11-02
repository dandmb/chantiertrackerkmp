package com.dmb.chantiertracker.presentation.help

import com.dmb.chantiertracker.presentation.billing.UrlOpener
import io.ktor.http.encodeURLParameter

fun interface SupportMailer {
    suspend fun compose(address: String, subject: String, body: String)
}

class MailtoSupportMailer(private val urlOpener: UrlOpener) : SupportMailer {
    override suspend fun compose(address: String, subject: String, body: String) =
        urlOpener.open("mailto:$address?subject=${subject.encodeURLParameter()}&body=${body.encodeURLParameter()}")
}
