package com.dmb.chantiertracker.data.remote

// The export endpoint sends both a quoted plain filename and a `filename*=`
// (RFC 5987) variant (see the backend `ProjectExportController.contentDisposition`).
// The plain one is already ASCII by construction — that's the only one we need.
// Mirrors the web's `parseContentDispositionFilename`.
private val PLAIN_FILENAME = Regex("""filename="([^"]+)"""")

/** The `filename="…"` value of a `Content-Disposition` header, or null. */
fun parseContentDispositionFilename(header: String?): String? =
    header?.let { PLAIN_FILENAME.find(it)?.groupValues?.get(1) }?.takeIf { it.isNotBlank() }
