package com.dmb.chantiertracker.domain.model

import kotlinx.io.RawSource

/**
 * A file the OS picker handed to the app, exposed as a **stream** rather than
 * a `ByteArray` (ADR-38). A raw phone video runs to hundreds of MB — loading
 * it whole crossed the Android heap ceiling and killed the process. Every
 * consumer reads it through [openSource] instead, one buffer at a time.
 *
 * [openSource] must be repeatable: Ktor re-sends the request body when the
 * auth plugin refreshes a token mid-upload, so the same [UploadFile] can be
 * asked for a fresh stream more than once.
 */
interface UploadFile {
    val name: String
    val mimeType: String
    fun size(): Long
    fun openSource(): RawSource
}
