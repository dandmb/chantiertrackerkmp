package com.dmb.chantiertracker.domain.model

/**
 * A project's PDF export — freshly generated server-side (nothing is stored,
 * every call regenerates from the project's current state) and written to a
 * throwaway file in the app cache.
 *
 * [path] is an absolute filesystem path the presentation layer hands to the
 * platform share / open mechanism; [fileName] is the download name the server
 * chose (from `Content-Disposition`, e.g. `chantier-villa-vidal-2026-09-08.pdf`).
 */
data class ExportedPdf(
    val path: String,
    val fileName: String,
)
