package com.dmb.chantiertracker.presentation.invitations

private const val SCHEME = "https"
private const val HOST = "chantiertracker.com"
private const val PATH_PREFIX = "/invitations/"

// ADR-59 — pure, tolerant by design (same idiom as parseCheckoutDeepLink):
// never throws on a URL this app links intent-filter wasn't actually meant
// to catch. Parsed by hand rather than a URL type: Kotlin/Native's stdlib
// has none in commonMain, and this shape (scheme + host + one path prefix)
// doesn't need one.
fun parseInvitationToken(url: String): String? {
    val withoutFragment = url.substringBefore('#')
    val withoutQuery = withoutFragment.substringBefore('?')
    val schemeSeparator = "://"
    val schemeIndex = withoutQuery.indexOf(schemeSeparator)
    if (schemeIndex == -1) return null
    val scheme = withoutQuery.substring(0, schemeIndex)
    if (!scheme.equals(SCHEME, ignoreCase = true)) return null
    val afterScheme = withoutQuery.substring(schemeIndex + schemeSeparator.length)
    val pathIndex = afterScheme.indexOf('/')
    val host = (if (pathIndex == -1) afterScheme else afterScheme.substring(0, pathIndex))
    if (!host.equals(HOST, ignoreCase = true)) return null
    val path = if (pathIndex == -1) "" else afterScheme.substring(pathIndex)
    if (!path.startsWith(PATH_PREFIX)) return null
    return path.substring(PATH_PREFIX.length).takeIf { it.isNotBlank() }
}
